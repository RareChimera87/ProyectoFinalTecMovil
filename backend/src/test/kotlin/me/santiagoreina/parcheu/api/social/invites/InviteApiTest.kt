package me.santiagoreina.parcheu.api.social.invites

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.matching.MatchFixture
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.GraphDto
import me.santiagoreina.parcheu.contract.InviteDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.contract.Redeem
import me.santiagoreina.parcheu.contract.RedeemResult
import me.santiagoreina.parcheu.contract.RedeemStatus
import java.util.UUID

/** Criterios de E6.1 de punta a punta: rutas, servicio, grafo en memoria y avisos. */
class InviteApiTest : FunSpec({
    val uids = listOf("ana", "beto", "caro", "dani")

    class World {
        val identity = InMemoryIdentityRepository().also { repo -> uids.forEach { repo.seedAcceptedUser(it, TEST_LEGAL_VERSION) } }
        val f = MatchFixture(idFor = { name -> identity.user(name).id }).also { f ->
            f.person("ana")
            f.person("beto")
            f.person("caro", modes = setOf(Mode.ESTUDIO))
            f.person("dani")
        }
        val ana: UUID = identity.user("ana").id
        val beto: UUID = identity.user("beto").id
        val caro: UUID = identity.user("caro").id
        val dani: UUID = identity.user("dani").id
        val invites = inMemoryInviteModule(f.clock, f.graph, f.simulation, f.notifier)

        fun edge(a: UUID, b: UUID, mode: Mode) = f.graphRepo.edge(a, b, mode)

        fun block(blocker: UUID, blocked: UUID) {
            f.graphRepo.block(blocker, blocked)
            f.block(blocker, blocked)
        }
    }

    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    fun ApplicationTestBuilder.app(w: World): HttpClient = startApp(
        testContainer(
            repo = w.identity, quota = w.f.quota, graph = w.f.graph, simulation = w.f.simulation, matching = w.f.module,
            clock = w.f.clock, notifier = w.f.notifier, invites = w.invites,
        ),
    )

    suspend fun HttpClient.me(uid: String): InviteDto = get("/v1/invites/me") { auth(uid) }.also { it.status shouldBe HttpStatusCode.OK }.body()

    suspend fun HttpClient.redeem(uid: String, code: String, mode: Mode = Mode.AMISTAD): HttpResponse = post("/v1/invites/redeem") {
        auth(uid)
        contentType(ContentType.Application.Json)
        setBody(Redeem(code, mode))
    }

    suspend fun HttpClient.accept(uid: String, target: String): HttpResponse = post("/v1/invites/$target/accept") { auth(uid) }

    test("GET /v1/invites/me: código ^[A-Z]{4}-[0-9]{4}$ estable, único por persona y su App Link") {
        val w = World()
        testApplication {
            val client = app(w)
            val ana = client.me("ana")
            ana.code shouldMatch "^[A-Z]{4}-[0-9]{4}$"
            ana.link shouldBe "https://parcheu-dev.web.app/i/${ana.code}"
            ana.pending.shouldBeEmpty()
            client.me("ana").code shouldBe ana.code
            val all = uids.map { client.me(it).code }
            all.toSet() shouldHaveSize uids.size
        }
    }

    test("canjear el propio código -> 400; un código mal formado -> 400; uno inexistente -> 404") {
        val w = World()
        testApplication {
            val client = app(w)
            val code = client.me("ana").code
            client.redeem("ana", code).apply {
                status shouldBe HttpStatusCode.BadRequest
                body<ApiError>().code shouldBe ErrorCode.VALIDATION
            }
            client.redeem("beto", "ABCD1234").status shouldBe HttpStatusCode.BadRequest
            client.redeem("beto", "ZZZZ-9999").apply {
                status shouldBe HttpStatusCode.NotFound
                body<ApiError>().code shouldBe ErrorCode.NOT_FOUND
            }
            w.edge(w.ana, w.beto, Mode.AMISTAD).shouldBeNull()
        }
    }

    test("canje -> arista PENDING y aviso INVITE_PENDING al dueño; canjear dos veces es idempotente") {
        val w = World()
        testApplication {
            val client = app(w)
            val code = client.me("ana").code
            val first = client.redeem("beto", code.lowercase()).also { it.status shouldBe HttpStatusCode.OK }.body<RedeemResult>()
            first.status shouldBe RedeemStatus.PENDING
            // Beto ya conoce a Ana (su nombre va con el QR), pero no recibe su id ni su foto hasta que acepte.
            first.other.id.shouldBeNull()
            first.other.photoUrl.shouldBeNull()
            first.other.anonId.shouldNotBeNull()
            first.other.label shouldBe "Nombre Secreto de ana"
            w.edge(w.ana, w.beto, Mode.AMISTAD)!!.state shouldBe EdgeState.PENDING

            val second = client.redeem("beto", code).also { it.status shouldBe HttpStatusCode.OK }.body<RedeemResult>()
            second shouldBe first
            w.edge(w.ana, w.beto, Mode.AMISTAD)!!.state shouldBe EdgeState.PENDING

            val sent = w.f.notifier.sent.filter { it.notification.kind == NotificationKinds.INVITE_PENDING }
            sent shouldHaveSize 1
            sent.single().userId shouldBe w.ana
            sent.single().notification.route shouldBe "parcheu://add-friends"
            sent.single().notification.actorId shouldBe w.beto

            // Ana ve a Beto en sus pendientes, con su nombre y el anonId que usa para aceptar.
            val pending = client.me("ana").pending.single()
            pending.mode shouldBe Mode.AMISTAD
            pending.other.label shouldBe "Nombre Secreto de beto"
            pending.other.id.shouldBeNull()
            pending.id shouldBe pending.other.anonId
            client.me("beto").pending.shouldBeEmpty()
        }
    }

    test("el dueño acepta -> CONNECTED en ese modo, aviso a quien canjeó; aceptar algo inexistente -> 404") {
        val w = World()
        testApplication {
            val client = app(w)
            val code = client.me("ana").code
            client.redeem("beto", code, Mode.ESTUDIO).status shouldBe HttpStatusCode.OK
            val pending = client.me("ana").pending.single()

            // Quien canjeó no puede aceptarse a sí mismo; nadie más puede aceptar por Ana.
            client.accept("beto", w.ana.toString()).status shouldBe HttpStatusCode.NotFound
            client.accept("caro", pending.id).status shouldBe HttpStatusCode.NotFound
            client.accept("ana", "a_noExisteNingunCanjeAsi1").apply {
                status shouldBe HttpStatusCode.NotFound
                body<ApiError>().code shouldBe ErrorCode.NOT_FOUND
            }
            client.accept("ana", UUID.randomUUID().toString()).status shouldBe HttpStatusCode.NotFound
            w.edge(w.ana, w.beto, Mode.ESTUDIO)!!.state shouldBe EdgeState.PENDING

            val accepted = client.accept("ana", pending.id).also { it.status shouldBe HttpStatusCode.OK }.body<RedeemResult>()
            accepted.status shouldBe RedeemStatus.CONNECTED
            accepted.other.id shouldBe w.beto.toString()
            accepted.other.label shouldBe "Nombre Secreto de beto"
            w.edge(w.ana, w.beto, Mode.ESTUDIO)!!.state shouldBe EdgeState.CONNECTED
            // Solo en el modo canjeado.
            w.edge(w.ana, w.beto, Mode.AMISTAD).shouldBeNull()
            client.me("ana").pending.shouldBeEmpty()

            val connected = w.f.notifier.sent.filter { it.notification.kind == NotificationKinds.NEW_CONNECTION }
            connected.single().userId shouldBe w.beto
            connected.single().notification.actorId shouldBe w.ana

            // Ya aceptado: aceptar otra vez es 404 y volver a canjear responde CONNECTED sin otro aviso.
            client.accept("ana", pending.id).status shouldBe HttpStatusCode.NotFound
            client.redeem("beto", code, Mode.ESTUDIO).body<RedeemResult>().status shouldBe RedeemStatus.CONNECTED
            w.f.notifier.sent.count { it.notification.kind == NotificationKinds.INVITE_PENDING } shouldBe 1

            // El grafo de Ana muestra a Beto conectado en Estudio.
            val graph = client.get("/v1/graph") { auth("ana") }.body<GraphDto>()
            graph.edges.single { it.otherId == w.beto.toString() }.state shouldBe EdgeState.CONNECTED
        }
    }

    test("el dueño también puede aceptar por users.id") {
        val w = World()
        testApplication {
            val client = app(w)
            client.redeem("beto", client.me("ana").code).status shouldBe HttpStatusCode.OK
            client.accept("ana", w.beto.toString()).body<RedeemResult>().status shouldBe RedeemStatus.CONNECTED
            w.edge(w.ana, w.beto, Mode.AMISTAD)!!.state shouldBe EdgeState.CONNECTED
        }
    }

    test("con bloqueo en cualquier sentido -> 403 BLOCKED y sin arista; un pendiente bloqueado no se puede aceptar") {
        val w = World()
        testApplication {
            val client = app(w)
            val anaCode = client.me("ana").code
            w.block(w.ana, w.beto)
            client.redeem("beto", anaCode).apply {
                status shouldBe HttpStatusCode.Forbidden
                body<ApiError>().code shouldBe ErrorCode.BLOCKED
            }
            w.edge(w.ana, w.beto, Mode.AMISTAD).shouldBeNull()

            val danisCode = client.me("dani").code
            w.block(w.ana, w.dani)
            client.redeem("ana", danisCode).body<ApiError>().code shouldBe ErrorCode.BLOCKED
            w.edge(w.ana, w.dani, Mode.AMISTAD).shouldBeNull()
        }
    }

    test("un canje previo a un bloqueo deja de verse y aceptarlo da 404 (sin arista CONNECTED)") {
        val w = World()
        testApplication {
            val client = app(w)
            client.redeem("beto", client.me("ana").code).status shouldBe HttpStatusCode.OK
            val pending = client.me("ana").pending.single()
            w.block(w.beto, w.ana)
            client.me("ana").pending.shouldBeEmpty()
            client.accept("ana", pending.id).status shouldBe HttpStatusCode.NotFound
            w.edge(w.ana, w.beto, Mode.AMISTAD)!!.state shouldNotBe EdgeState.CONNECTED
        }
    }

    test("el modo debe estar activo para los dos (modo común) -> 400") {
        val w = World()
        testApplication {
            val client = app(w)
            val caroCode = client.me("caro").code
            client.redeem("ana", caroCode, Mode.AMISTAD).apply {
                status shouldBe HttpStatusCode.BadRequest
                body<ApiError>().code shouldBe ErrorCode.VALIDATION
            }
            client.redeem("ana", caroCode, Mode.ESTUDIO).body<RedeemResult>().status shouldBe RedeemStatus.PENDING
        }
    }

    test("si los dos canjearon el código del otro en el mismo modo, quedan CONNECTED sin esperar") {
        val w = World()
        testApplication {
            val client = app(w)
            val anaCode = client.me("ana").code
            val betoCode = client.me("beto").code
            client.redeem("beto", anaCode).body<RedeemResult>().status shouldBe RedeemStatus.PENDING
            val mutual = client.redeem("ana", betoCode).body<RedeemResult>()
            mutual.status shouldBe RedeemStatus.CONNECTED
            mutual.other.id shouldBe w.beto.toString()
            w.edge(w.ana, w.beto, Mode.AMISTAD)!!.state shouldBe EdgeState.CONNECTED
            client.me("ana").pending.shouldBeEmpty()
            client.me("beto").pending.shouldBeEmpty()
        }
    }

    test("un código cuyo dueño borró la cuenta ya no existe") {
        val w = World()
        testApplication {
            val client = app(w)
            val code = client.me("dani").code
            w.f.graphRepo.addPerson(w.f.graphRepo.people.getValue(w.dani).copy(deletedAt = w.f.clock.instant()))
            client.redeem("ana", code).status shouldBe HttpStatusCode.NotFound
            w.edge(w.ana, w.dani, Mode.AMISTAD).shouldBeNull()
        }
    }
})
