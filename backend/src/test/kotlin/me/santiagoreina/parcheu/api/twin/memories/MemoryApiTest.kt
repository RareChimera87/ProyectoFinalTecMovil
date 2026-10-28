package me.santiagoreina.parcheu.api.twin.memories

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.ai.FakeLlmClient
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.platform.auth.FakeOidcVerifier
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.api.twin.DerivedTwinUserLookup
import me.santiagoreina.parcheu.api.twin.InMemoryTwinRepository
import me.santiagoreina.parcheu.api.twin.PERSONA_TEXT
import me.santiagoreina.parcheu.api.twin.answersOf
import me.santiagoreina.parcheu.api.twin.inMemoryTwinModule
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.MemoryDto
import me.santiagoreina.parcheu.contract.MemoryStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Page
import me.santiagoreina.parcheu.contract.TwinDto
import java.time.Instant
import java.util.UUID

/** `GET /v1/twin/memories`, `accept|reject`, la tarea `extract-memories` y el job `memories` por HTTP. */
class MemoryApiTest : FunSpec({
    val now: Instant = FIXED_CLOCK.instant()
    val ana = DerivedTwinUserLookup.idFor("ana")
    val beto = DerivedTwinUserLookup.idFor("beto")

    class Fixture {
        val fake = FakeLlmClient(textResponder = { PERSONA_TEXT })
        val gateway = testGateway(fake)
        val twins = InMemoryTwinRepository()
        val memories = InMemoryMemoryRepository(twins)
        val queue = FakeTaskQueue()
        val module = inMemoryTwinModule(FIXED_CLOCK, gateway, twins, queue, runInline = false, memoryRepo = memories)
    }

    suspend fun ApplicationTestBuilder.app(f: Fixture): HttpClient = startApp(
        testContainer(
            repo = InMemoryIdentityRepository().also {
                it.seedAcceptedUser("ana", TEST_LEGAL_VERSION)
                it.seedAcceptedUser("beto", TEST_LEGAL_VERSION)
            },
            twin = f.module,
            ai = f.gateway,
        ),
    )

    fun bearer(uid: String) = "Bearer ${FakeTokenVerifier.tokenFor(uid)}"

    suspend fun HttpClient.getAs(uid: String, path: String) = get(path) { header(HttpHeaders.Authorization, bearer(uid)) }

    suspend fun HttpClient.postAs(uid: String, path: String) = post(path) { header(HttpHeaders.Authorization, bearer(uid)) }

    suspend fun HttpClient.internal(path: String, body: String? = null) = post(path) {
        header(HttpHeaders.Authorization, "Bearer ${FakeOidcVerifier.VALID_TOKEN}")
        if (body != null) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    test("GET /v1/twin/memories devuelve el modo de cada memoria (contrato MemoryDto para el chip de C4)") {
        testApplication {
            val f = Fixture()
            f.memories.addMemory(ana, Mode.PAREJA, "Le gustan las cenas tranquilas", now.minusSeconds(300))
            f.memories.addMemory(ana, Mode.ESTUDIO, "Repasa con mapas mentales", now.minusSeconds(200))
            f.memories.addMemory(beto, Mode.AMISTAD, "Memoria de otra persona", now.minusSeconds(100))
            val client = app(f)

            val response = client.getAs("ana", "/v1/twin/memories")
            response.status shouldBe HttpStatusCode.OK
            // El JSON crudo lleva `mode` en cada item: es lo que C4 pinta como ModeChip.
            val raw = ContractJson.parseToJsonElement(response.bodyAsText()).jsonObject
            val items = raw["items"] as JsonArray
            items.map { (it as JsonObject)["mode"]!!.jsonPrimitive.content } shouldBe listOf("ESTUDIO", "PAREJA")
            items.forEach { (it as JsonObject).keys shouldBe setOf("id", "text", "mode", "status", "createdAt") }

            val page = response.body<Page<MemoryDto>>()
            page.items.map { it.mode to it.text } shouldBe listOf(
                Mode.ESTUDIO to "Repasa con mapas mentales",
                Mode.PAREJA to "Le gustan las cenas tranquilas",
            )
            page.items.all { it.status == MemoryStatus.PENDING } shouldBe true
            page.nextCursor shouldBe null
        }
    }

    test("GET /v1/twin/memories filtra por status y pagina por cursor") {
        testApplication {
            val f = Fixture()
            (1..5).forEach { i -> f.memories.addMemory(ana, Mode.AMISTAD, "Memoria número $i", now.minusSeconds(1000L - i)) }
            f.memories.addMemory(ana, Mode.AMISTAD, "Ya aceptada", now.minusSeconds(10), MemoryStatus.ACCEPTED)
            val client = app(f)

            client.getAs("ana", "/v1/twin/memories?status=ACCEPTED").body<Page<MemoryDto>>().items.map { it.text } shouldBe listOf("Ya aceptada")

            val first = client.getAs("ana", "/v1/twin/memories?status=PENDING&limit=2").body<Page<MemoryDto>>()
            first.items.map { it.text } shouldBe listOf("Memoria número 5", "Memoria número 4")
            val second = client.getAs("ana", "/v1/twin/memories?status=PENDING&limit=2&cursor=${first.nextCursor}").body<Page<MemoryDto>>()
            second.items.map { it.text } shouldBe listOf("Memoria número 3", "Memoria número 2")
            val third = client.getAs("ana", "/v1/twin/memories?status=PENDING&limit=2&cursor=${second.nextCursor}").body<Page<MemoryDto>>()
            third.items.map { it.text } shouldBe listOf("Memoria número 1")
            third.nextCursor shouldBe null

            client.getAs("ana", "/v1/twin/memories?status=OTRO").body<ApiError>().code shouldBe ErrorCode.VALIDATION
            client.getAs("ana", "/v1/twin/memories?cursor=basura").body<ApiError>().code shouldBe ErrorCode.VALIDATION
            client.getAs("ana", "/v1/twin/memories?limit=0").body<ApiError>().code shouldBe ErrorCode.VALIDATION
        }
    }

    test("aceptar una memoria sube twins.version en 1 y devuelve MemoryDto ACCEPTED; la de otro da 404") {
        testApplication {
            val f = Fixture()
            val client = app(f)
            client.post("/v1/twin/answers") {
                header(HttpHeaders.Authorization, bearer("ana"))
                contentType(ContentType.Application.Json)
                setBody(answersOf())
            }.status shouldBe HttpStatusCode.OK
            val before = client.getAs("ana", "/v1/twin").body<TwinDto>().version
            val mine = f.memories.addMemory(ana, Mode.AMISTAD, "Le gusta el café", now.minusSeconds(60))
            val other = f.memories.addMemory(beto, Mode.AMISTAD, "Memoria de Beto", now.minusSeconds(60))

            val accepted = client.postAs("ana", "/v1/twin/memories/${mine.id}/accept")
            accepted.status shouldBe HttpStatusCode.OK
            accepted.body<MemoryDto>().let {
                it.status shouldBe MemoryStatus.ACCEPTED
                it.mode shouldBe Mode.AMISTAD
                it.id shouldBe mine.id.toString()
            }
            client.getAs("ana", "/v1/twin").body<TwinDto>().version shouldBe before + 1

            client.postAs("ana", "/v1/twin/memories/${other.id}/accept").status shouldBe HttpStatusCode.NotFound
            client.postAs("ana", "/v1/twin/memories/${UUID.randomUUID()}/reject").status shouldBe HttpStatusCode.NotFound
            client.postAs("ana", "/v1/twin/memories/${mine.id}/reject").body<ApiError>().code shouldBe ErrorCode.CONFLICT
            client.getAs("ana", "/v1/twin").body<TwinDto>().version shouldBe before + 1
        }
    }

    test("rechazar deja la memoria REJECTED sin subir la versión") {
        testApplication {
            val f = Fixture()
            val memory = f.memories.addMemory(ana, Mode.ESTUDIO, "Estudia de noche", now.minusSeconds(60))
            val client = app(f)
            client.postAs("ana", "/v1/twin/memories/${memory.id}/reject").body<MemoryDto>().status shouldBe MemoryStatus.REJECTED
            f.memories.all(ana).single().status shouldBe MemoryStatus.REJECTED
        }
    }

    test("/internal/jobs/memories encola y /internal/tasks/extract-memories extrae; ambas exigen OIDC") {
        testApplication {
            val f = Fixture()
            f.memories.setLearning(ana, Mode.AMISTAD, true)
            f.memories.addMessage(ana, UUID.randomUUID(), Mode.AMISTAD, "Me encanta el pan de bono", now.minusSeconds(600))
            val client = app(f)

            client.post("/internal/jobs/memories").status shouldBe HttpStatusCode.Forbidden
            client.post("/internal/tasks/extract-memories").status shouldBe HttpStatusCode.Forbidden

            client.internal("/internal/jobs/memories").status shouldBe HttpStatusCode.NoContent
            f.queue.tasks shouldHaveSize 1
            val task = f.queue.tasks.single()
            task.path shouldBe MemoryService.TASK_PATH

            client.internal("/internal/tasks/extract-memories", task.body).status shouldBe HttpStatusCode.NoContent
            val listed = client.getAs("ana", "/v1/twin/memories").body<Page<MemoryDto>>()
            listed.items shouldHaveSize 1
            listed.items.single().mode shouldBe Mode.AMISTAD
            f.fake.lastRequest!!.prompt shouldContain "pan de bono"

            // Reintento de la misma tarea: no duplica.
            client.internal("/internal/tasks/extract-memories", task.body).status shouldBe HttpStatusCode.NoContent
            client.getAs("ana", "/v1/twin/memories").body<Page<MemoryDto>>().items shouldHaveSize 1
            client.internal("/internal/tasks/extract-memories", """{"id":"no-uuid"}""").status shouldBe HttpStatusCode.BadRequest
        }
    }
})
