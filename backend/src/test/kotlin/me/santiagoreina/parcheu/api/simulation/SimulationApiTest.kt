package me.santiagoreina.parcheu.api.simulation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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

        /** Arista CONNECTED entre los dos en [modes]: `POST /v1/simulations` la exige en el modo pedido. */
        fun connect(a: String, b: String, vararg modes: Mode = arrayOf(Mode.AMISTAD)) =
            modes.forEach { sim.graphRepo.putEdge(id(a), id(b), it, EdgeState.CONNECTED, 70) }
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
        w.connect("ana", "beto")
        testApplication {
            val client = app(w)
            val response = client.start("ana", w.id("beto").toString())
            response.status shouldBe HttpStatusCode.Accepted
            val summary = response.body<SimulationSummary>()
            summary.status shouldBe SimulationStatus.QUEUED
            summary.mode shouldBe Mode.AMISTAD
            summary.origin shouldBe SimulationOrigin.PARCHE
            summary.score shouldBe null
            // Con conexión mutua (la que exige el POST) la otra persona sale con nombre.
            summary.other.label shouldBe "beto"
            summary.other.id shouldBe w.id("beto").toString()
            summary.other.anonId shouldBe null
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

    test("sin arista CONNECTED en el modo pedido responde 404 genérico sin revelar modos, edad, bloqueo ni cobrar") {
        val w = World()
        // eli tiene Pareja activo y edad confirmada: si el POST no exigiera la arista de Pareja, ana simularía con ella.
        w.connect("ana", "eli")
        // hugo bloqueó a ana después de conectar: el bloqueo corta la conexión y el ego-grafo lo oculta.
        w.connect("ana", "hugo")
        w.sim.graphRepo.block(w.id("hugo"), w.id("ana"))
        w.sim.repo.block(w.id("hugo"), w.id("ana"))
        w.sim.exhaustBudget()
        testApplication {
            val client = app(w)
            val cases = listOf(
                "fede" to Mode.AMISTAD, // existe, pero no está conectado con ana
                "eli" to Mode.PAREJA, // conectado solo en Amistad
                "eli" to Mode.ESTUDIO, // conectado solo en Amistad
                "hugo" to Mode.AMISTAD, // bloqueado: no se distingue de no encontrado
            )
            val unknown = client.start("ana", UUID.randomUUID().toString())
            val bodies = (cases.map { (uid, mode) -> client.start("ana", w.id(uid).toString(), mode) } + unknown).map { response ->
                response.status shouldBe HttpStatusCode.NotFound
                response.error().code shouldBe ErrorCode.NOT_FOUND
                response.error().message
            }
            // El mismo mensaje para todos: ni el modo de la otra persona ni el bloqueo se cuelan en el texto.
            bodies.toSet() shouldHaveSize 1
            w.sim.usage.charges shouldBe emptyList()
            w.sim.repo.all() shouldBe emptyList()
            w.sim.queue.tasks shouldBe emptyList()
        }
    }

    test("conectados en Pareja pueden simular en Pareja; la arista de Amistad no sirve para Estudio") {
        val w = World()
        w.connect("ana", "eli", Mode.AMISTAD, Mode.PAREJA)
        testApplication {
            val client = app(w)
            client.start("ana", w.id("eli").toString(), Mode.PAREJA).status shouldBe HttpStatusCode.Accepted
            client.start("ana", w.id("eli").toString(), Mode.AMISTAD).status shouldBe HttpStatusCode.Accepted
            client.start("ana", w.id("eli").toString(), Mode.ESTUDIO).status shouldBe HttpStatusCode.NotFound
            // La arista vale en los dos sentidos.
            client.start("eli", w.id("ana").toString(), Mode.PAREJA).status shouldBe HttpStatusCode.Accepted
        }
    }

    test("GET /v1/simulations pagina por cursor, más recientes primero, solo las propias") {
        val w = World()
        testApplication {
            val client = app(w)
            listOf("beto", "caro", "dani").forEach {
                w.connect("ana", it)
                client.start("ana", w.id(it).toString()).status shouldBe HttpStatusCode.Accepted
            }
            w.connect("eli", "fede")
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
        w.connect("ana", "beto")
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

    test("validaciones: id inválido, consigo mismo, modo inactivo, bloqueo en carrera (404) y edad en Pareja") {
        val w = World(parejaAge = false)
        w.sim.person("caro", modes = setOf(Mode.ESTUDIO), id = w.id("caro"))
        // La arista de Amistad sigue aunque caro haya apagado el modo (HomeSuggestions lo cuenta igual).
        w.connect("ana", "caro")
        w.connect("ana", "beto", Mode.PAREJA)
        // Carrera: la comprobación de conexión aún ve la arista, pero el bloqueo ya está cuando create lo mira
        // (aquí, porque solo lo ve la lectura de simulación). También es el 404 genérico, nunca BLOCKED.
        w.connect("ana", "dani")
        w.sim.repo.block(w.id("dani"), w.id("ana"))
        testApplication {
            val client = app(w)
            client.start("ana", "no-es-uuid").error().code shouldBe ErrorCode.VALIDATION
            client.start("ana", w.id("ana").toString()).error().code shouldBe ErrorCode.VALIDATION
            client.start("ana", UUID.randomUUID().toString()).status shouldBe HttpStatusCode.NotFound
            client.start("ana", w.id("caro").toString(), Mode.AMISTAD).error().code shouldBe ErrorCode.VALIDATION
            val blocked = client.start("ana", w.id("dani").toString())
            blocked.status shouldBe HttpStatusCode.NotFound
            blocked.error().code shouldBe ErrorCode.NOT_FOUND
            blocked.error().message shouldBe client.start("ana", UUID.randomUUID().toString()).error().message
            client.start("ana", w.id("beto").toString(), Mode.PAREJA).error().code shouldBe ErrorCode.AGE_REQUIRED
            w.sim.usage.charges shouldBe emptyList()
            w.sim.repo.all() shouldBe emptyList()
        }
    }

    test("con el presupuesto de IA agotado responde 503 AI_BUDGET con retryAt y no cobra cupo") {
        val w = World()
        w.connect("ana", "beto")
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
            listOf("beto", "caro", "dani", "eli", "fede", "gabi").forEach { w.connect("ana", it) }
            listOf("beto", "caro", "dani", "eli", "fede").forEach { client.start("ana", w.id(it).toString()).status shouldBe HttpStatusCode.Accepted }
            val sixth = client.start("ana", w.id("gabi").toString())
            sixth.status shouldBe HttpStatusCode.PaymentRequired
            sixth.error().code shouldBe ErrorCode.QUOTA_EXCEEDED
            sixth.error().kind shouldBe "SIMULATION"
        }
    }
})
