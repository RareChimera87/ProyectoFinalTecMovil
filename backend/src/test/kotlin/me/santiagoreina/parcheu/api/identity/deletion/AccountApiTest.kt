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

    test("repetir DELETE /v1/me conserva la fecha de borrado y reencola con un nombre propio (no lo deduplica Cloud Tasks)") {
        val f = Fixture()
        testApplication {
            val client = startApp(f.container)
            client.delete("/v1/me") { auth("ana") }.status shouldBe HttpStatusCode.Accepted
            client.delete("/v1/me") { auth("ana") }.status shouldBe HttpStatusCode.Accepted
            client.delete("/v1/me") { auth("ana") }.status shouldBe HttpStatusCode.Accepted
            val ana = f.repo.user("ana")
            ana.deletedAt shouldBe FIXED_CLOCK.instant()
            // La primera tarea pudo agotar sus reintentos: la repetición no puede reusar su nombre. Dentro del
            // mismo minuto sí se deduplica (doble toque).
            f.queue.tasks.map { it.name } shouldContainExactly listOf(
                "purge-user-${ana.id}",
                "purge-user-${ana.id}-retry-${FIXED_CLOCK.instant().epochSecond / 60}",
            )
            f.queue.tasks.map { it.body }.distinct() shouldContainExactly listOf("""{"id":"${ana.id}"}""")
        }
    }

    test("POST /v1/session con un token anterior al borrado no recrea una cuenta purgada (lápida): 401") {
        val f = Fixture()
        testApplication {
            val client = startApp(f.container)
            // Lo que deja la purga: ya no está la fila de la persona, sí su lápida.
            f.repo.createIfAbsent(tombstoneUid("zoraida"), email = null, displayName = null, now = FIXED_CLOCK.instant())
            val response = client.post("/v1/session") { auth("zoraida") }
            response.status shouldBe HttpStatusCode.Unauthorized
            response.body<ApiError>().code shouldBe ErrorCode.UNAUTHENTICATED
            f.repo.users.none { it.firebaseUid == "zoraida" } shouldBe true
            // Una cuenta nueva (otro uid, como el que da Firebase Auth al volver a entrar) sí se crea.
            client.post("/v1/session") { auth("zoraida-nueva") }.status shouldBe HttpStatusCode.OK
        }
    }

    test("purge-sweep exige OIDC y reencola con nombre por hora las cuentas borradas que siguen sin purgar") {
        val f = Fixture()
        testApplication {
            val client = startApp(f.container)
            val id = f.repo.user("ana").id
            f.purge.plans[id] = f.plan(id)

            client.post("/internal/jobs/purge-sweep").status shouldBe HttpStatusCode.Forbidden
            client.post("/internal/jobs/purge-sweep") { auth("ana") }.status shouldBe HttpStatusCode.Forbidden
            f.queue.tasks.shouldBeEmpty()

            client.post("/internal/jobs/purge-sweep") { tasks() }.status shouldBe HttpStatusCode.NoContent
            val task = f.queue.tasks.single()
            task.queue shouldBe QueueKind.MAINTENANCE
            task.path shouldBe "/tasks/purge-user"
            task.name shouldBe "purge-user-$id-sweep-${FIXED_CLOCK.instant().epochSecond / 3600}"
            task.body shouldBe """{"id":"$id"}"""
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
            // Segunda pasada sobre simulations/* después de Postgres (una simulación en curso pudo republicar).
            f.mirror.removedSimulations shouldContainExactly listOf(plan.simulationIds)
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

    test("GET /v1/me/export devuelve el perfil propio; no exige términos (derecho de acceso)") {
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
            client.get("/v1/me/export") { auth("sin-terminos") }.status shouldBe HttpStatusCode.OK
            // Sin sesión previa sigue respondiendo 404, como el resto de /v1/me.
            client.get("/v1/me/export") { auth("desconocida") }.status shouldBe HttpStatusCode.NotFound
        }
    }
})
