package me.santiagoreina.parcheu.api.graph.communities

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
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
import io.ktor.server.testing.testApplication
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.ai.FakeLlmClient
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.graph.InMemoryGraphRepository
import me.santiagoreina.parcheu.api.graph.inMemoryGraphModule
import me.santiagoreina.parcheu.api.graph.person
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.platform.auth.FakeOidcVerifier
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.quota.inMemoryQuotaModule
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.CommunitiesDto
import java.util.UUID

/** `/v1/communities`, el job semanal y la tarea de nombre de punta a punta con Fakes (E6.4). */
class CommunityApiTest : FunSpec({
    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")
    fun HttpRequestBuilder.oidc() = header(HttpHeaders.Authorization, "Bearer ${FakeOidcVerifier.VALID_TOKEN}")

    class Fixture {
        val identityRepo = InMemoryIdentityRepository().also { it.seedAcceptedUser("ana", TEST_LEGAL_VERSION) }
        val anaId: UUID = identityRepo.user("ana").id
        val graphRepo = InMemoryGraphRepository()
        val quota = inMemoryQuotaModule(FIXED_CLOCK)
        val queue = FakeTaskQueue()
        val llm = FakeLlmClient(textResponder = { "Los del tinto de las 7" })
        val graph = inMemoryGraphModule(FIXED_CLOCK, graphRepo)
        val communities = inMemoryCommunityModule(graph, testGateway(llm), quota, FIXED_CLOCK, queue)

        init {
            val ana = person(0, name = "Ana").copy(id = anaId).also(graphRepo::addPerson)
            val others = graphRepo.group(1, 3)
            graphRepo.clique(listOf(ana) + others)
        }

        fun container() = testContainer(repo = identityRepo, quota = quota, graph = graph, communities = communities)
    }

    test("gratis recibe solo el conteo: sin items en el JSON") {
        val f = Fixture()
        f.communities.service.runWeekly()
        testApplication {
            val client = startApp(f.container())
            val response = client.get("/v1/communities") { auth("ana") }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe """{"count":1}"""
        }
    }

    test("ParcheU+ recibe la lista con miembros por la policy y nunca el uuid de quien no conoce") {
        val f = Fixture()
        f.quota.devPlus!!.grantPlus(f.anaId, 30, FIXED_CLOCK.instant())
        f.communities.service.runWeekly()
        testApplication {
            val client = startApp(f.container())
            val dto = client.get("/v1/communities") { auth("ana") }.body<CommunitiesDto>()
            dto.count shouldBe 1
            val item = dto.items!!.single()
            item.size shouldBe 4
            item.members.size shouldBe 3
            item.name shouldBe CommunityDetector.PENDING_NAME
            item.members.all { it.id != null && it.anonId == null } shouldBe true
        }
    }

    test("sin comunidades devuelve conteo 0 y, con Plus, lista vacía") {
        val f = Fixture()
        f.quota.devPlus!!.grantPlus(f.anaId, 30, FIXED_CLOCK.instant())
        testApplication {
            val client = startApp(f.container())
            val dto = client.get("/v1/communities") { auth("ana") }.body<CommunitiesDto>()
            dto.count shouldBe 0
            dto.items shouldBe emptyList()
        }
    }

    test("sin token -> 401; sin sesión -> 404") {
        val f = Fixture()
        testApplication {
            val client = startApp(f.container())
            client.get("/v1/communities").status shouldBe HttpStatusCode.Unauthorized
            client.get("/v1/communities") { auth("nadie") }.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("las rutas internas exigen OIDC y el job encola y la tarea nombra la comunidad") {
        val f = Fixture()
        testApplication {
            val client = startApp(f.container())
            client.post("/internal/jobs/communities").status shouldBe HttpStatusCode.Forbidden
            client.post("/internal/jobs/communities") { auth("ana") }.status shouldBe HttpStatusCode.Forbidden
            client.post("/internal/tasks/name-community").status shouldBe HttpStatusCode.Forbidden

            client.post("/internal/jobs/communities") { oidc() }.status shouldBe HttpStatusCode.NoContent
            val task = f.queue.tasks.single()
            task.path shouldBe "/tasks/name-community"

            client.post("/internal${task.path}") {
                oidc()
                contentType(ContentType.Application.Json)
                setBody(task.body)
            }.status shouldBe HttpStatusCode.NoContent
            f.communities.repository.all().single().name shouldBe "Los del tinto de las 7"

            client.post("/internal/tasks/name-community") {
                oidc()
                contentType(ContentType.Application.Json)
                setBody("""{"id":"no-es-uuid"}""")
            }.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
