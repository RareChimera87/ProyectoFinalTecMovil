package me.santiagoreina.parcheu.api.graph.fof

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
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
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.InMemoryGraphRepository
import me.santiagoreina.parcheu.api.graph.inMemoryGraphModule
import me.santiagoreina.parcheu.api.graph.person
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.quota.inMemoryQuotaModule
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.FofDto
import me.santiagoreina.parcheu.contract.Mode
import java.util.UUID

/** `GET /v1/graph/fof` de punta a punta con Fakes: gratis solo `count`, ParcheU+ la lista. */
class FofApiTest : FunSpec({
    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

    class Fixture {
        val identityRepo = InMemoryIdentityRepository().also { it.seedAcceptedUser("ana", TEST_LEGAL_VERSION) }
        val anaId: UUID = identityRepo.user("ana").id
        val graphRepo = InMemoryGraphRepository()
        val quota = inMemoryQuotaModule(FIXED_CLOCK)
        val beto = person(1, name = "Beto")
        val dani = person(2, name = "Dani")

        init {
            graphRepo.addPerson(person(0, name = "Ana").copy(id = anaId))
            graphRepo.addPerson(beto)
            graphRepo.addPerson(dani)
            graphRepo.putEdge(anaId, beto.id, Mode.AMISTAD, EdgeState.CONNECTED, 85)
            graphRepo.putEdge(beto.id, dani.id, Mode.ESTUDIO, EdgeState.CONNECTED, 90)
        }

        fun container() = testContainer(repo = identityRepo, quota = quota, graph = inMemoryGraphModule(FIXED_CLOCK, graphRepo))
    }

    test("gratis recibe solo el conteo: sin items en el JSON") {
        val f = Fixture()
        testApplication {
            val client = startApp(f.container())
            val response = client.get("/v1/graph/fof") { auth("ana") }
            response.status shouldBe HttpStatusCode.OK
            val text = response.bodyAsText()
            text shouldBe """{"count":1}"""
            text shouldNotContain "items"
            response.body<FofDto>().items.shouldBeNull()
        }
    }

    test("ParcheU+ recibe la lista con anonId, nunca el uuid real") {
        val f = Fixture()
        f.quota.devPlus!!.grantPlus(f.anaId, 30, FIXED_CLOCK.instant())
        testApplication {
            val client = startApp(f.container())
            val response = client.get("/v1/graph/fof") { auth("ana") }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldNotContain f.dani.id.toString()
            val dto = response.body<FofDto>()
            dto.count shouldBe 1
            val item = dto.items!!.single()
            item.anonId shouldBe AnonIdSigner.forTests().anonId(f.anaId, f.dani.id)
            item.mutuals shouldBe 1
            item.mode shouldBe Mode.ESTUDIO
            item.label shouldBe "Alguien de la UIS"
            // Origen de la línea punteada: el amigo en común (conexión directa, con su users.id).
            item.viaId shouldBe f.beto.id.toString()
        }
    }

    test("sin token -> 401; sin sesión -> 404") {
        val f = Fixture()
        testApplication {
            val client = startApp(f.container())
            client.get("/v1/graph/fof").status shouldBe HttpStatusCode.Unauthorized
            client.get("/v1/graph/fof") { auth("nadie") }.status shouldBe HttpStatusCode.NotFound
        }
    }
})
