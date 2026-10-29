package me.santiagoreina.parcheu.api.simulation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
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
import me.santiagoreina.parcheu.api.TEST_OIDC_TOKEN
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Page
import me.santiagoreina.parcheu.contract.SimulationDto
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.SimulationSummary
import me.santiagoreina.parcheu.contract.Speaker
import me.santiagoreina.parcheu.contract.StartSimulation
import me.santiagoreina.parcheu.contract.TranscriptDto
import java.util.UUID

class SimulationApiTest : FunSpec({
    val uids = listOf("ana", "beto", "caro", "dani", "eli", "fede", "gabi", "hugo")

    /** Identidad con usuarios que aceptaron términos y un mundo de simulación con sus mismos ids. */
    class World(inline: Boolean = true, parejaAge: Boolean = true) {
        val identity = InMemoryIdentityRepository().also { repo -> uids.forEach { repo.seedAcceptedUser(it, TEST_LEGAL_VERSION) } }
        val sim = SimFixture(inline = inline)
        val ids = uids.associateWith { uid ->
            sim.person(uid, id = identity.user(uid).id, ageVerified = parejaAge || uid != "beto")
        }

        fun id(uid: String) = ids.getValue(uid)
    }

    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    suspend fun ApplicationTestBuilder.app(w: World): HttpClient = startApp(
        testContainer(repo = w.identity, ai = w.sim.ai, quota = w.sim.quota, graph = w.sim.graph, simulation = w.sim.module),
    )

    suspend fun HttpClient.start(uid: String, other: String, mode: Mode = Mode.AMISTAD) = post("/v1/simulations") {
        auth(uid)
        contentType(ContentType.Application.Json)
        setBody(StartSimulation(other, mode))
    }

    suspend fun HttpClient.simulateTask(id: String, oidc: Boolean = true) = post("/internal/tasks/simulate") {
        if (oidc) header(HttpHeaders.Authorization, "Bearer ${TEST_OIDC_TOKEN}")
        contentType(ContentType.Application.Json)
        setBody("""{"id":"$id"}""")
    }

    suspend fun HttpResponse.error() = body<ApiError>()

    test("POST /v1/simulations responde 202 QUEUED; la tarea interna la lleva a DONE y GET la muestra con puntaje") {
        val w = World(inline = false)
        testApplication {
            val client = app(w)
            val response = client.start("ana", w.id("beto").toString())
            response.status shouldBe HttpStatusCode.Accepted
            val summary = response.body<SimulationSummary>()
            summary.status shouldBe SimulationStatus.QUEUED
            summary.mode shouldBe Mode.AMISTAD
            summary.origin shouldBe SimulationOrigin.PARCHE
            summary.score shouldBe null
            // Sin conexión mutua la otra persona sale anónima.
            summary.other.label shouldBe "Alguien de la UIS"
            summary.other.id shouldBe null
            summary.other.anonId!! shouldStartWith "a_"
            w.sim.queue.tasks.single().path shouldBe "/tasks/simulate"
            w.sim.queue.tasks.single().name shouldBe "simulate-${summary.id}"

            client.simulateTask(summary.id, oidc = false).status shouldBe HttpStatusCode.Forbidden
            client.simulateTask(summary.id).status shouldBe HttpStatusCode.NoContent
            client.simulateTask(summary.id).status shouldBe HttpStatusCode.NoContent

            val dto = client.get("/v1/simulations/${summary.id}") { auth("ana") }.body<SimulationDto>()
            dto.summary.status shouldBe SimulationStatus.DONE
            dto.score shouldNotBe null
            dto.summary.score shouldBe dto.score
            dto.progress.of shouldBe MAX_TURNS_PER_SCENARIO
            // La otra persona también la ve; un tercero recibe 404.
            client.get("/v1/simulations/${summary.id}") { auth("beto") }.status shouldBe HttpStatusCode.OK
            client.get("/v1/simulations/${summary.id}") { auth("caro") }.status shouldBe HttpStatusCode.NotFound
            client.get("/v1/simulations/no-es-uuid") { auth("ana") }.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("con conexión mutua la otra persona sale con nombre") {
        val w = World()
        w.sim.graphRepo.putEdge(w.id("ana"), w.id("beto"), Mode.AMISTAD, EdgeState.CONNECTED, 70)
        testApplication {
            val summary = app(w).start("ana", w.id("beto").toString()).body<SimulationSummary>()
            summary.other.label shouldBe "beto"
            summary.other.id shouldBe w.id("beto").toString()
            summary.other.anonId shouldBe null
        }
    }

    test("GET /v1/simulations pagina por cursor, más recientes primero, solo las propias") {
        val w = World()
        testApplication {
            val client = app(w)
            listOf("beto", "caro", "dani").forEach { client.start("ana", w.id(it).toString()).status shouldBe HttpStatusCode.Accepted }
            client.start("eli", w.id("fede").toString())
            val first = client.get("/v1/simulations?limit=2") { auth("ana") }.body<Page<SimulationSummary>>()
            first.items shouldHaveSize 2
            first.nextCursor shouldNotBe null
            val second = client.get("/v1/simulations?limit=2&cursor=${first.nextCursor}") { auth("ana") }.body<Page<SimulationSummary>>()
            second.items shouldHaveSize 1
            second.nextCursor shouldBe null
            (first.items + second.items).map { it.id }.toSet().size shouldBe 3
            // beto ve la simulación que ana pidió con él.
            client.get("/v1/simulations") { auth("beto") }.body<Page<SimulationSummary>>().items shouldHaveSize 1
            client.get("/v1/simulations?limit=0") { auth("ana") }.status shouldBe HttpStatusCode.BadRequest
            client.get("/v1/simulations?cursor=xx") { auth("ana") }.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("la transcripción es de ParcheU+ y se ve desde quien consulta (ME/OTHER)") {
        val w = World()
        testApplication {
            val client = app(w)
            val id = client.start("ana", w.id("beto").toString()).body<SimulationSummary>().id
            val free = client.get("/v1/simulations/$id/transcript") { auth("ana") }
            free.status shouldBe HttpStatusCode.PaymentRequired
            free.error().code shouldBe ErrorCode.PLUS_REQUIRED

            w.sim.plus(w.id("ana"))
            w.sim.plus(w.id("beto"))
            val mine = client.get("/v1/simulations/$id/transcript") { auth("ana") }.body<TranscriptDto>()
            mine.scenarios shouldHaveSize 3
            mine.scenarios.first().turns.first().speaker shouldBe Speaker.ME
            val theirs = client.get("/v1/simulations/$id/transcript") { auth("beto") }.body<TranscriptDto>()
            theirs.scenarios.first().turns.first().speaker shouldBe Speaker.OTHER
            theirs.scenarios.map { it.title } shouldBe mine.scenarios.map { it.title }
        }
    }

    test("validaciones: id inválido, consigo mismo, modo inactivo, bloqueo y edad en Pareja") {
        val w = World(parejaAge = false)
        w.sim.person("caro", modes = setOf(Mode.ESTUDIO), id = w.id("caro"))
        w.sim.repo.block(w.id("dani"), w.id("ana"))
        testApplication {
            val client = app(w)
            client.start("ana", "no-es-uuid").error().code shouldBe ErrorCode.VALIDATION
            client.start("ana", w.id("ana").toString()).error().code shouldBe ErrorCode.VALIDATION
            client.start("ana", UUID.randomUUID().toString()).status shouldBe HttpStatusCode.NotFound
            client.start("ana", w.id("caro").toString(), Mode.AMISTAD).error().code shouldBe ErrorCode.VALIDATION
            val blocked = client.start("ana", w.id("dani").toString())
            blocked.status shouldBe HttpStatusCode.Forbidden
            blocked.error().code shouldBe ErrorCode.BLOCKED
            client.start("ana", w.id("beto").toString(), Mode.PAREJA).error().code shouldBe ErrorCode.AGE_REQUIRED
            w.sim.usage.charges shouldBe emptyList()
            w.sim.repo.all() shouldBe emptyList()
        }
    }

    test("con el presupuesto de IA agotado responde 503 AI_BUDGET con retryAt y no cobra cupo") {
        val w = World()
        w.sim.exhaustBudget()
        testApplication {
            val response = app(w).start("ana", w.id("beto").toString())
            response.status shouldBe HttpStatusCode.ServiceUnavailable
            val error = response.error()
            error.code shouldBe ErrorCode.AI_BUDGET
            error.retryAt shouldNotBe null
            w.sim.usage.charges shouldBe emptyList()
        }
    }

    test("la 6.a simulación gratis de la semana responde 402 QUOTA_EXCEEDED con kind SIMULATION") {
        val w = World()
        testApplication {
            val client = app(w)
            listOf("beto", "caro", "dani", "eli", "fede").forEach { client.start("ana", w.id(it).toString()).status shouldBe HttpStatusCode.Accepted }
            val sixth = client.start("ana", w.id("gabi").toString())
            sixth.status shouldBe HttpStatusCode.PaymentRequired
            sixth.error().code shouldBe ErrorCode.QUOTA_EXCEEDED
            sixth.error().kind shouldBe "SIMULATION"
        }
    }
})
