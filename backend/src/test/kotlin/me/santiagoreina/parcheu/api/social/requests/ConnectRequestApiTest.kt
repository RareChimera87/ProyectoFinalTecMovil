package me.santiagoreina.parcheu.api.social.requests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.platform.AppContainer
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.simulation.SimFixture
import me.santiagoreina.parcheu.api.simulation.inMemorySimulationModule
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.ConnectRequestsDto
import me.santiagoreina.parcheu.contract.Decision
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.StartSimulation
import me.santiagoreina.parcheu.contract.Visibility
import java.util.UUID

/** `GET /v1/connect-requests` (backlog E6.6) por HTTP con todo en memoria. */
class ConnectRequestApiTest : FunSpec({
    val uids = listOf("ana", "beto", "caro", "dani")

    class World {
        val identity = InMemoryIdentityRepository().also { repo -> uids.forEach { repo.seedAcceptedUser(it, TEST_LEGAL_VERSION) } }
        val sim = SimFixture()
        val ids = uids.associateWith { uid -> sim.person(uid, id = identity.user(uid).id) }
        val module = inMemorySimulationModule(FIXED_CLOCK, sim.ai, sim.quota, sim.graph, sim.repo, sim.queue, sim.mirror, sim.notifier)

        fun id(uid: String) = ids.getValue(uid)

        /** Simulación DONE pedida por [a] con [b]. */
        fun done(a: String, b: String, mode: Mode = Mode.AMISTAD): String =
            runBlocking { module.service.start(id(a), StartSimulation(id(b).toString(), mode)).id }

        fun container(): AppContainer =
            testContainer(repo = identity, quota = sim.quota, ai = sim.ai, graph = sim.graph, simulation = module)
    }

    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    fun ApplicationTestBuilder.app(w: World): HttpClient = startApp(w.container())

    suspend fun HttpClient.decide(uid: String, id: String, connect: Boolean) = post("/v1/simulations/$id/decision") {
        auth(uid)
        contentType(ContentType.Application.Json)
        setBody(Decision(connect))
    }

    suspend fun HttpClient.requests(uid: String) = get("/v1/connect-requests") { auth(uid) }

    test("gratis recibe count sin lista: el JSON no trae items") {
        val w = World()
        val id = w.done("ana", "beto")
        testApplication {
            val client = app(w)
            client.decide("beto", id, connect = true).status shouldBe HttpStatusCode.OK
            val response = client.requests("ana")
            response.status shouldBe HttpStatusCode.OK
            val text = response.bodyAsText()
            text shouldBe """{"count":1}"""
            text shouldNotContain "items"
            ConnectRequestsDto(count = 1).items.shouldBeNull()
        }
    }

    test("Plus recibe la lista con la persona enmascarada, la afinidad y la simulación") {
        val w = World()
        val id = w.done("ana", "beto")
        w.sim.plus(w.id("ana"))
        testApplication {
            val client = app(w)
            client.decide("beto", id, connect = true)
            val response = client.requests("ana")
            response.bodyAsText() shouldNotContain w.id("beto").toString()
            val dto = response.body<ConnectRequestsDto>()
            dto.count shouldBe 1
            val item = dto.items!!.single()
            item.simulationId shouldBe id
            item.other.id.shouldBeNull()
            item.other.photoUrl.shouldBeNull()
            item.other.anonId shouldBe AnonIdSigner.forTests().anonId(w.id("ana"), w.id("beto"))
            item.other.label shouldBe "Alguien de la UIS"
            item.affinity shouldBe w.sim.repo.find(UUID.fromString(id))!!.score
        }
    }

    test("sin decisiones ajenas no hay solicitudes; quien conecta no se ve a sí mismo en su lista") {
        val w = World()
        val id = w.done("ana", "beto")
        w.sim.plus(w.id("ana"))
        w.sim.plus(w.id("beto"))
        testApplication {
            val client = app(w)
            client.requests("ana").body<ConnectRequestsDto>().let {
                it.count shouldBe 0
                it.items.orEmpty().shouldBeEmpty()
            }
            client.decide("beto", id, connect = true)
            client.requests("beto").body<ConnectRequestsDto>().count shouldBe 0
            client.requests("ana").body<ConnectRequestsDto>().count shouldBe 1
        }
    }

    test("tras decidir, la persona desaparece de la lista: conectar y 'Ahora no'") {
        val w = World()
        val first = w.done("ana", "beto")
        val second = w.done("ana", "caro")
        w.sim.plus(w.id("ana"))
        testApplication {
            val client = app(w)
            client.decide("beto", first, connect = true)
            client.decide("caro", second, connect = true)
            client.requests("ana").body<ConnectRequestsDto>().items!! shouldHaveSize 2

            // 'Ahora no' a Beto: sale de la lista.
            client.decide("ana", first, connect = false)
            client.requests("ana").body<ConnectRequestsDto>().let { dto ->
                dto.count shouldBe 1
                dto.items!!.single().simulationId shouldBe second
            }
            // Conectar con Caro: hay conexión mutua y también sale.
            client.decide("ana", second, connect = true)
            client.requests("ana").body<ConnectRequestsDto>().let {
                it.count shouldBe 0
                it.items!!.shouldBeEmpty()
            }
        }
    }

    test("no aparecen bloqueados, HIDDEN ni borrados; una persona con dos simulaciones cuenta una vez") {
        val w = World()
        val blocked = w.done("ana", "beto")
        val hidden = w.done("ana", "caro")
        val a = w.done("ana", "dani")
        val b = w.done("ana", "dani", Mode.ESTUDIO)
        w.sim.plus(w.id("ana"))
        testApplication {
            val client = app(w)
            listOf("beto" to blocked, "caro" to hidden, "dani" to a).forEach { (uid, id) -> client.decide(uid, id, true) }
            client.decide("dani", b, true)
            client.requests("ana").body<ConnectRequestsDto>().count shouldBe 3

            w.sim.graphRepo.block(w.id("ana"), w.id("beto"))
            w.sim.graphRepo.addPerson(PersonRow(w.id("caro"), "caro", null, "UIS", Visibility.HIDDEN, false, null))
            val dto = client.requests("ana").body<ConnectRequestsDto>()
            dto.count shouldBe 1
            dto.items!!.single().other.anonId shouldBe AnonIdSigner.forTests().anonId(w.id("ana"), w.id("dani"))
        }
    }

    test("quien ya está conectado en ese modo no cuenta como solicitud") {
        val w = World()
        val id = w.done("ana", "beto")
        w.sim.plus(w.id("ana"))
        testApplication {
            val client = app(w)
            client.decide("beto", id, connect = true)
            w.sim.graphRepo.putEdge(w.id("ana"), w.id("beto"), Mode.AMISTAD, EdgeState.CONNECTED, 80)
            client.requests("ana").body<ConnectRequestsDto>().count shouldBe 0
        }
    }

    test("sin token -> 401; sin sesión -> 404") {
        val w = World()
        testApplication {
            val client = app(w)
            client.get("/v1/connect-requests").status shouldBe HttpStatusCode.Unauthorized
            client.get("/v1/connect-requests") { auth("nadie") }.status shouldBe HttpStatusCode.NotFound
        }
    }
})
