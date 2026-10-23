package me.santiagoreina.parcheu.api.graph

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.identity.IdentityService
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.identity.profile.InMemoryProfileRepository
import me.santiagoreina.parcheu.api.identity.profile.ProfileService
import me.santiagoreina.parcheu.api.platform.AppContainer
import me.santiagoreina.parcheu.api.quota.inMemoryQuotaModule
import me.santiagoreina.parcheu.api.twin.inMemoryTwinModule
import me.santiagoreina.parcheu.api.platform.allDomains
import me.santiagoreina.parcheu.api.platform.auth.FakeOidcVerifier
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testConfig
import me.santiagoreina.parcheu.api.TASKS_ACCOUNT
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.GraphDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NodeDetail
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import java.time.Instant
import java.util.UUID

/** `GET /v1/graph` y `GET /v1/graph/nodes/{id}` de punta a punta con Fakes. */
class GraphApiTest : FunSpec({
    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    class Fixture {
        val identityRepo = InMemoryIdentityRepository().also { it.seedAcceptedUser("ana", TEST_LEGAL_VERSION) }
        val anaId: UUID = identityRepo.user("ana").id
        val graphRepo = InMemoryGraphRepository()
        val laura = person(1, name = "Laura Pérez")
        val match = person(2, name = "Mario Díaz")
        val simId: UUID = UUID.randomUUID()

        init {
            graphRepo.addPerson(person(0, name = "Ana").copy(id = anaId))
            graphRepo.addPerson(laura)
            graphRepo.addPerson(match)
            graphRepo.putEdge(anaId, laura.id, Mode.AMISTAD, EdgeState.CONNECTED, 87)
            graphRepo.putEdge(anaId, laura.id, Mode.PAREJA, EdgeState.SIMULATED, 74)
            graphRepo.putEdge(anaId, match.id, Mode.ESTUDIO, EdgeState.SIMULATED, 66)
            graphRepo.simulations += Triple(
                match.id,
                anaId,
                SimulationRow(simId, SimulationStatus.DONE, Mode.ESTUDIO, SimulationOrigin.RANDOM, 66, Instant.parse("2026-10-24T10:00:00Z")),
            )
        }

        fun container(): AppContainer {
            val identity = IdentityService(identityRepo, FIXED_CLOCK, TEST_LEGAL_VERSION)
            val profile = ProfileService(identity, InMemoryProfileRepository(identityRepo), FIXED_CLOCK)
            val graph = inMemoryGraphModule(FIXED_CLOCK, graphRepo)
            val quota = inMemoryQuotaModule(FIXED_CLOCK)
            val twin = inMemoryTwinModule(FIXED_CLOCK, testGateway())
            return AppContainer(
                config = testConfig(),
                clock = FIXED_CLOCK,
                tokenVerifier = FakeTokenVerifier(),
                oidcVerifier = FakeOidcVerifier(TASKS_ACCOUNT),
                identity = identity,
                profile = profile,
                quota = quota,
                twin = twin,
                graph = graph,
                domains = allDomains(identity, profile, quota, twin, graph),
            )
        }
    }

    test("GET /v1/graph devuelve el ego-grafo con Pareja, anónimos y stats") {
        val f = Fixture()
        testApplication {
            val client = startApp(f.container())
            val response = client.get("/v1/graph") { auth("ana") }
            response.status shouldBe HttpStatusCode.OK
            val graph = response.body<GraphDto>()

            graph.me.id shouldBe f.anaId.toString()
            graph.me.label shouldBe "Ana"
            graph.nodes.map { it.label } shouldContainExactlyInAnyOrder listOf("Laura Pérez", "Alguien de la UIS")
            graph.edges.map { it.mode } shouldContainExactlyInAnyOrder listOf(Mode.AMISTAD, Mode.PAREJA, Mode.ESTUDIO)
            graph.stats.connections shouldBe 1
            graph.stats.simulated shouldBe 1
        }
    }

    test("GET /v1/graph?mode=PAREJA&minAffinity=70 filtra") {
        val f = Fixture()
        testApplication {
            val client = startApp(f.container())
            val graph = client.get("/v1/graph?mode=PAREJA&minAffinity=70") { auth("ana") }.body<GraphDto>()
            graph.edges.map { it.otherId to it.mode } shouldBe listOf(f.laura.id.toString() to Mode.PAREJA)
            client.get("/v1/graph?minAffinity=75") { auth("ana") }.body<GraphDto>().edges.single().affinity shouldBe 87
        }
    }

    test("parámetros inválidos -> 400 VALIDATION") {
        val f = Fixture()
        testApplication {
            val client = startApp(f.container())
            listOf("/v1/graph?mode=CITA", "/v1/graph?minAffinity=200", "/v1/graph?community=x").forEach { url ->
                val response = client.get(url) { auth("ana") }
                response.status shouldBe HttpStatusCode.BadRequest
                response.body<ApiError>().code shouldBe ErrorCode.VALIDATION
            }
        }
    }

    test("GET /v1/graph/nodes/{id} con anonId da la última simulación sin exponer el uuid") {
        val f = Fixture()
        testApplication {
            val client = startApp(f.container())
            val anonId = client.get("/v1/graph") { auth("ana") }.body<GraphDto>().nodes.single { it.anonymous }.id

            val detail = client.get("/v1/graph/nodes/$anonId") { auth("ana") }.body<NodeDetail>()
            detail.node.label shouldBe "Alguien de la UIS"
            detail.lastSimulation?.id shouldBe f.simId.toString()
            detail.lastSimulation?.other?.id shouldBe null
            detail.lastSimulation?.other?.anonId shouldBe anonId
            detail.sharedModes shouldBe listOf(Mode.ESTUDIO)

            val laura = client.get("/v1/graph/nodes/${f.laura.id}") { auth("ana") }.body<NodeDetail>()
            laura.sharedModes shouldBe listOf(Mode.AMISTAD, Mode.PAREJA)

            client.get("/v1/graph/nodes/${f.match.id}") { auth("ana") }.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("sin token -> 401; sin sesión -> 404") {
        val f = Fixture()
        testApplication {
            val client = startApp(f.container())
            client.get("/v1/graph").status shouldBe HttpStatusCode.Unauthorized
            client.get("/v1/graph") { auth("nadie") }.status shouldBe HttpStatusCode.NotFound
        }
    }
})
