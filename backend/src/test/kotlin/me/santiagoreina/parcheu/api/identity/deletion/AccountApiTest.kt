package me.santiagoreina.parcheu.api.identity.deletion

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.graph.inMemoryGraphModule
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.identity.export.ExportRows
import me.santiagoreina.parcheu.api.platform.auth.FakeOidcVerifier
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.platform.tasks.QueueKind
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.twin.inMemoryTwinModule
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.ExportDto
import java.util.UUID

/** `DELETE /v1/me`, `GET /v1/me/export` y la tarea `purge-user` de punta a punta con Fakes (E8.3). */
class AccountApiTest : FunSpec({
    fun HttpRequestBuilder.auth(uid: String) = header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")
    fun HttpRequestBuilder.tasks() = header(HttpHeaders.Authorization, "Bearer ${FakeOidcVerifier.VALID_TOKEN}")

    class Fixture {
        val repo = InMemoryIdentityRepository().also { it.seedAcceptedUser("ana", TEST_LEGAL_VERSION) }
        val queue = FakeTaskQueue()
        val purge = InMemoryAccountPurgeRepository()
        val mirror = RecordingAccountMirrorCleaner()
        val auth = FakeAuthAccountRemover()
        private val graph = inMemoryGraphModule(FIXED_CLOCK)
        private val twin = inMemoryTwinModule(FIXED_CLOCK, testGateway())
        val container = testContainer(repo = repo, graph = graph, twin = twin) { identity ->
            inMemoryAccountModule(identity, graph, twin, FIXED_CLOCK, queue, purge, mirror, auth) { id ->
                ExportRows(consents = repo.consents.filter { it.first == id }.map { it.second })
            }
        }

        fun plan(userId: UUID) = PurgePlan(userId, "uid-x", "Ana", listOf(UUID.randomUUID()), emptyList())
    }

    test("DELETE /v1/me responde 202, marca la cuenta como borrada y encola purge-user en maintenance") {
        val f = Fixture()
        testApplication {
            val client = startApp(f.container)
            client.delete("/v1/me") { auth("ana") }.status shouldBe HttpStatusCode.Accepted
            val ana = f.repo.user("ana")
            ana.deletedAt shouldBe FIXED_CLOCK.instant()
            f.queue.tasks shouldHaveSize 1
            val task = f.queue.tasks.single()
            task.queue shouldBe QueueKind.MAINTENANCE
            task.path shouldBe "/tasks/purge-user"
            task.name shouldBe "purge-user-${ana.id}"
            task.body shouldBe """{"id":"${ana.id}"}"""

            // Invisible en todo desde ya: la sesión deja de servir.
            val me = client.get("/v1/me") { auth("ana") }
            me.status shouldBe HttpStatusCode.Unauthorized
            me.body<ApiError>().code shouldBe ErrorCode.UNAUTHENTICATED
            client.post("/v1/session") { auth("ana") }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("repetir DELETE /v1/me es idempotente: misma fecha de borrado y una sola tarea") {
        val f = Fixture()
        testApplication {
            val client = startApp(f.container)
            client.delete("/v1/me") { auth("ana") }.status shouldBe HttpStatusCode.Accepted
            client.delete("/v1/me") { auth("ana") }.status shouldBe HttpStatusCode.Accepted
            f.repo.user("ana").deletedAt shouldBe FIXED_CLOCK.instant()
            f.queue.tasks shouldHaveSize 1
        }
    }

    test("si encolar falla, DELETE responde error y el reintento vuelve a encolar") {
        val f = Fixture()
        testApplication {
            val client = startApp(f.container)
            f.queue.failWith = IllegalStateException("Cloud Tasks caído")
            client.delete("/v1/me") { auth("ana") }.status shouldBe HttpStatusCode.InternalServerError
            f.queue.failWith = null
            client.delete("/v1/me") { auth("ana") }.status shouldBe HttpStatusCode.Accepted
            f.queue.tasks shouldHaveSize 1
        }
    }

    test("DELETE /v1/me sin sesión previa responde 404") {
        val f = Fixture()
        testApplication {
            startApp(f.container).delete("/v1/me") { auth("desconocida") }.status shouldBe HttpStatusCode.NotFound
            f.queue.tasks.shouldBeEmpty()
        }
    }

    test("purge-user exige OIDC y ejecuta Firestore, Firebase Auth y Postgres, en ese orden") {
        val f = Fixture()
        testApplication {
            val client = startApp(f.container)
            client.delete("/v1/me") { auth("ana") }
            val id = f.repo.user("ana").id
            val plan = f.plan(id)
            f.purge.plans[id] = plan

            fun body() = """{"id":"$id"}"""
            client.post("/internal/tasks/purge-user") {
                contentType(ContentType.Application.Json); setBody(body())
            }.status shouldBe HttpStatusCode.Forbidden
            client.post("/internal/tasks/purge-user") {
                auth("ana"); contentType(ContentType.Application.Json); setBody(body())
            }.status shouldBe HttpStatusCode.Forbidden
            f.purge.purged.shouldBeEmpty()

            client.post("/internal/tasks/purge-user") {
                tasks(); contentType(ContentType.Application.Json); setBody(body())
            }.status shouldBe HttpStatusCode.NoContent
            f.mirror.plans shouldContainExactly listOf(plan)
            f.auth.removed shouldContainExactly listOf("uid-x")
            f.purge.purged shouldContainExactly listOf(id)

            // Reintento de Cloud Tasks: ya no hay nada que purgar.
            client.post("/internal/tasks/purge-user") {
                tasks(); contentType(ContentType.Application.Json); setBody(body())
            }.status shouldBe HttpStatusCode.NoContent
            f.purge.purged shouldHaveSize 1
            f.auth.removed shouldHaveSize 1
        }
    }

    test("si Firebase Auth falla, Postgres no se purga y la tarea se reintenta (500)") {
        val f = Fixture()
        testApplication {
            val client = startApp(f.container)
            val id = f.repo.user("ana").id
            f.purge.plans[id] = f.plan(id)
            f.auth.failWith = IllegalStateException("Auth caído")
            client.post("/internal/tasks/purge-user") {
                tasks(); contentType(ContentType.Application.Json); setBody("""{"id":"$id"}""")
            }.status shouldBe HttpStatusCode.InternalServerError
            f.purge.purged.shouldBeEmpty()
            f.purge.plans[id] shouldNotBe null
        }
    }

    test("purge-user con un id que no es uuid responde 400") {
        val f = Fixture()
        testApplication {
            startApp(f.container).post("/internal/tasks/purge-user") {
                tasks(); contentType(ContentType.Application.Json); setBody("""{"id":"no-uuid"}""")
            }.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /v1/me/export devuelve el perfil propio; exige términos como cualquier ruta") {
        val f = Fixture()
        testApplication {
            val client = startApp(f.container)
            val response = client.get("/v1/me/export") { auth("ana") }
            response.status shouldBe HttpStatusCode.OK
            val export = response.body<ExportDto>()
            export.me.id shouldBe f.repo.user("ana").id.toString()
            export.consents.map { it.kind.name } shouldContainExactly listOf("TERMS", "PRIVACY")
            export.generatedAt.toString() shouldBe "2026-10-25T12:00:00Z"

            client.post("/v1/session") { auth("sin-terminos") }
            val gated = client.get("/v1/me/export") { auth("sin-terminos") }
            gated.status shouldBe HttpStatusCode.Forbidden
            gated.body<ApiError>().code shouldBe ErrorCode.TERMS_REQUIRED
        }
    }
})
