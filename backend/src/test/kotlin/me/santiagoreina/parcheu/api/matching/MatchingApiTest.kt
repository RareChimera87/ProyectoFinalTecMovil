package me.santiagoreina.parcheu.api.matching

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
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
import me.santiagoreina.parcheu.api.TEST_OIDC_TOKEN
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.Choice
import me.santiagoreina.parcheu.contract.Enqueue
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.contract.MatchDto
import me.santiagoreina.parcheu.contract.MatchStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.TicketDto
import me.santiagoreina.parcheu.contract.TicketStatus

class MatchingApiTest : FunSpec({
    val uids = listOf("ana", "beto", "caro")

    class World {
        val identity = InMemoryIdentityRepository().also { repo -> uids.forEach { repo.seedAcceptedUser(it, TEST_LEGAL_VERSION) } }
        val f = MatchFixture(idFor = { name -> identity.user(name).id }).also { f -> uids.forEach { f.person(it) } }
    }

    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    fun ApplicationTestBuilder.app(w: World): HttpClient = startApp(
        testContainer(
            repo = w.identity, quota = w.f.quota, graph = w.f.graph, simulation = w.f.simulation, matching = w.f.module,
            clock = w.f.clock, notifier = w.f.notifier,
        ),
    )

    suspend fun HttpClient.enqueue(uid: String, mode: Mode = Mode.AMISTAD) = post("/v1/match/queue") {
        auth(uid)
        contentType(ContentType.Application.Json)
        setBody(Enqueue(mode))
    }

    suspend fun HttpClient.choose(uid: String, id: String, choice: MatchChoice) = post("/v1/matches/$id/choice") {
        auth(uid)
        contentType(ContentType.Application.Json)
        setBody(Choice(choice))
    }

    suspend fun HttpResponse.error() = body<ApiError>()

    test("la fila exige token") {
        testApplication {
            val client = app(World())
            client.post("/v1/match/queue").status shouldBe HttpStatusCode.Unauthorized
            client.get("/v1/matches/x").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("POST /v1/match/queue deja WAITING, el segundo usuario recibe MATCHED y GET lo refleja") {
        testApplication {
            val client = app(World())
            val first = client.enqueue("ana")
            first.status shouldBe HttpStatusCode.OK
            first.body<TicketDto>().status shouldBe TicketStatus.WAITING
            val second = client.enqueue("beto").body<TicketDto>()
            second.status shouldBe TicketStatus.MATCHED
            second.matchId.shouldNotBeNull()
            client.get("/v1/match/queue") { auth("ana") }.body<TicketDto>().matchId shouldBe second.matchId

            val match = client.get("/v1/matches/${second.matchId}") { auth("ana") }.body<MatchDto>()
            match.status shouldBe MatchStatus.PENDING_CHOICE
            match.mode shouldBe Mode.AMISTAD
            match.scenarioAvailable shouldBe false
            match.other.label shouldBe "Alguien de la UIS"
        }
    }

    test("choice: SIMULATION resuelve; repetir o elegir sobre un match resuelto es 409; SCENARIO apagado es 400; ajeno es 404") {
        testApplication {
            val client = app(World())
            client.enqueue("ana")
            val id = client.enqueue("beto").body<TicketDto>().matchId!!
            val scenario = client.choose("ana", id, MatchChoice.SCENARIO)
            scenario.status shouldBe HttpStatusCode.BadRequest
            scenario.error().code shouldBe ErrorCode.VALIDATION
            val ok = client.choose("ana", id, MatchChoice.SIMULATION)
            ok.status shouldBe HttpStatusCode.OK
            ok.body<MatchDto>().run {
                status shouldBe MatchStatus.SIMULATION
                myChoice shouldBe MatchChoice.SIMULATION
            }
            val again = client.choose("ana", id, MatchChoice.SIMULATION)
            again.status shouldBe HttpStatusCode.Conflict
            again.error().code shouldBe ErrorCode.CONFLICT
            client.choose("beto", id, MatchChoice.SIMULATION).status shouldBe HttpStatusCode.Conflict
            client.get("/v1/matches/$id") { auth("caro") }.status shouldBe HttpStatusCode.NotFound
            client.choose("caro", id, MatchChoice.SIMULATION).status shouldBe HttpStatusCode.NotFound
        }
    }

    test("DELETE /v1/match/queue responde 204, es idempotente y deja la fila vacía") {
        testApplication {
            val client = app(World())
            client.enqueue("ana")
            client.delete("/v1/match/queue") { auth("ana") }.status shouldBe HttpStatusCode.NoContent
            client.delete("/v1/match/queue") { auth("ana") }.status shouldBe HttpStatusCode.NoContent
            client.get("/v1/match/queue") { auth("ana") }.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST sin cupo es 402 QUOTA_EXCEEDED y entrar dos veces a la fila es 409") {
        testApplication {
            val w = World()
            val client = app(w)
            client.enqueue("ana")
            client.enqueue("ana").error().code shouldBe ErrorCode.CONFLICT
            w.f.quota.service.consume(w.f.idFor("caro"), me.santiagoreina.parcheu.contract.QuotaKind.RANDOM_MATCH, "uso-previo")
            val response = client.enqueue("caro")
            response.status shouldBe HttpStatusCode.PaymentRequired
            response.error().code shouldBe ErrorCode.QUOTA_EXCEEDED
        }
    }

    test("Pareja sin edad verificada es 403 AGE_REQUIRED y un modo apagado es 400") {
        testApplication {
            val w = World()
            val sin = w.identity.user("beto").id
            w.f.person("beto", ageVerified = false, id = sin, modes = setOf(Mode.AMISTAD, Mode.PAREJA))
            val client = app(w)
            val pareja = client.enqueue("beto", Mode.PAREJA)
            pareja.status shouldBe HttpStatusCode.Forbidden
            pareja.error().code shouldBe ErrorCode.AGE_REQUIRED
            client.enqueue("beto", Mode.ESTUDIO).error().code shouldBe ErrorCode.VALIDATION
        }
    }

    test("/internal/jobs/match-sweep exige OIDC y responde 204") {
        testApplication {
            val client = app(World())
            client.post("/internal/jobs/match-sweep").status shouldBe HttpStatusCode.Forbidden
            client.post("/internal/jobs/match-sweep") { header(HttpHeaders.Authorization, "Bearer ${TEST_OIDC_TOKEN}") }
                .status shouldBe HttpStatusCode.NoContent
        }
    }
})
