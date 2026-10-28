package me.santiagoreina.parcheu.api.graph.history

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.graph.InMemoryGraphRepository
import me.santiagoreina.parcheu.api.graph.inMemoryGraphModule
import me.santiagoreina.parcheu.api.graph.person
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.quota.inMemoryQuotaModule
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.HistoryDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.MomentKind
import java.time.Instant
import java.util.UUID

/** `GET /v1/graph/history` de punta a punta con Fakes (E6.5). FIXED_CLOCK = 25 oct 2026, Bogotá. */
class HistoryApiTest : FunSpec({
    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    class Fixture {
        val identityRepo = InMemoryIdentityRepository().also { it.seedAcceptedUser("ana", TEST_LEGAL_VERSION) }
        val anaId: UUID = identityRepo.user("ana").id
        val graphRepo = InMemoryGraphRepository()
        val quota = inMemoryQuotaModule(FIXED_CLOCK)

        init {
            graphRepo.addPerson(person(0, name = "Ana").copy(id = anaId))
        }

        fun friend(n: Int, mode: Mode, state: EdgeState, affinity: Int?, at: String) {
            val p = person(n, name = "Amigo $n")
            graphRepo.addPerson(p)
            graphRepo.putEdge(anaId, p.id, mode, state, affinity, Instant.parse(at))
        }

        fun plus() = quota.devPlus!!.grantPlus(anaId, 30, FIXED_CLOCK.instant())

        fun container() = testContainer(repo = identityRepo, quota = quota, graph = inMemoryGraphModule(FIXED_CLOCK, graphRepo))
    }

    test("tres meses de aristas devuelven tres meses con los conteos esperados") {
        val f = Fixture()
        f.friend(1, Mode.AMISTAD, EdgeState.CONNECTED, 70, "2026-08-10T15:00:00Z") // ago: 1 conexión
        f.friend(2, Mode.ESTUDIO, EdgeState.CONNECTED, 85, "2026-09-05T15:00:00Z") // sep: 2
        f.friend(3, Mode.AMISTAD, EdgeState.CONNECTED, 60, "2026-09-20T15:00:00Z")
        f.friend(4, Mode.AMISTAD, EdgeState.SIMULATED, 55, "2026-10-02T15:00:00Z") // oct: solo un nodo
        f.friend(5, Mode.AMISTAD, EdgeState.CONNECTED, 90, "2026-10-12T15:00:00Z") // oct: 1
        f.plus()
        testApplication {
            val client = startApp(f.container())
            val response = client.get("/v1/graph/history") { auth("ana") }
            response.status shouldBe HttpStatusCode.OK
            val dto = response.body<HistoryDto>()
            dto.months.map { it.month } shouldContainExactly listOf("2026-08", "2026-09", "2026-10")
            dto.months.map { it.connections } shouldContainExactly listOf(1, 3, 4)
            dto.months.map { it.newConnections } shouldContainExactly listOf(1, 2, 1)
            dto.months.map { it.nodes } shouldContainExactly listOf(1, 3, 5)
            dto.moments.map { it.kind } shouldContainExactly listOf(
                MomentKind.FIRST_CONNECTION, MomentKind.HIGHEST_AFFINITY,
            )
            dto.moments.first().other!!.label shouldBe "Amigo 1"
            dto.moments.last().value shouldBe 90
        }
    }

    test("la serie no deja huecos entre meses y llega hasta el mes actual") {
        val f = Fixture()
        f.friend(1, Mode.AMISTAD, EdgeState.CONNECTED, 70, "2026-08-10T15:00:00Z")
        f.plus()
        testApplication {
            val client = startApp(f.container())
            val dto = client.get("/v1/graph/history") { auth("ana") }.body<HistoryDto>()
            dto.months.map { it.month } shouldContainExactly listOf("2026-08", "2026-09", "2026-10")
            dto.months.map { it.connections } shouldContainExactly listOf(1, 1, 1)
            dto.months.map { it.newConnections } shouldContainExactly listOf(1, 0, 0)
        }
    }

    test("los hitos de 10 y 25 conexiones aparecen con su fecha y valor") {
        val f = Fixture()
        (1..26).forEach { n ->
            val day = "%02d".format(n.coerceAtMost(28))
            f.friend(n, Mode.AMISTAD, EdgeState.CONNECTED, 50, "2026-09-${day}T15:00:00Z")
        }
        f.plus()
        testApplication {
            val client = startApp(f.container())
            val dto = client.get("/v1/graph/history") { auth("ana") }.body<HistoryDto>()
            val milestones = dto.moments.filter { it.kind == MomentKind.CONNECTIONS_10 || it.kind == MomentKind.CONNECTIONS_25 }
            milestones.map { it.kind } shouldContainExactly listOf(MomentKind.CONNECTIONS_10, MomentKind.CONNECTIONS_25)
            milestones.map { it.value } shouldContainExactly listOf(10, 25)
            dto.moments.none { it.kind == MomentKind.CONNECTIONS_50 } shouldBe true
        }
    }

    test("sin aristas: listas vacías") {
        val f = Fixture()
        f.plus()
        testApplication {
            val client = startApp(f.container())
            val dto = client.get("/v1/graph/history") { auth("ana") }.body<HistoryDto>()
            dto.months.shouldBeEmpty()
            dto.moments.shouldBeEmpty()
        }
    }

    test("gratis -> 402 PLUS_REQUIRED sin datos de la red") {
        val f = Fixture()
        f.friend(1, Mode.AMISTAD, EdgeState.CONNECTED, 70, "2026-08-10T15:00:00Z")
        testApplication {
            val client = startApp(f.container())
            val response = client.get("/v1/graph/history") { auth("ana") }
            response.status shouldBe HttpStatusCode.PaymentRequired
            val text = response.bodyAsText()
            text shouldContainCode "PLUS_REQUIRED"
            text shouldNotContain "Amigo 1"
        }
    }

    test("un bloqueado no cuenta ni aparece en los momentos") {
        val f = Fixture()
        f.friend(1, Mode.AMISTAD, EdgeState.CONNECTED, 99, "2026-09-10T15:00:00Z")
        f.friend(2, Mode.AMISTAD, EdgeState.CONNECTED, 60, "2026-09-12T15:00:00Z")
        f.graphRepo.block(f.anaId, person(1).id)
        f.plus()
        testApplication {
            val client = startApp(f.container())
            val response = client.get("/v1/graph/history") { auth("ana") }
            response.bodyAsText() shouldNotContain "Amigo 1"
            val dto = response.body<HistoryDto>()
            dto.months.last().connections shouldBe 1
            dto.moments.first().other!!.label shouldBe "Amigo 2"
        }
    }

    test("sin token -> 401") {
        val f = Fixture()
        testApplication {
            val client = startApp(f.container())
            client.get("/v1/graph/history").status shouldBe HttpStatusCode.Unauthorized
        }
    }
})

private infix fun String.shouldContainCode(code: String) {
    (code in this) shouldBe true
}
