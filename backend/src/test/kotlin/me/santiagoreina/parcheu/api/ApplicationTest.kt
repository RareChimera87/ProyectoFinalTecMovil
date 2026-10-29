package me.santiagoreina.parcheu.api

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import io.ktor.server.testing.testApplication
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.auth.FakeOidcVerifier
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.platform.errors.AiBudgetExceeded
import me.santiagoreina.parcheu.api.platform.errors.Conflict
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.QuotaExceeded
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.Health
import me.santiagoreina.parcheu.contract.QuotaKind
import kotlin.time.Instant

/** Dominio de prueba que lanza excepciones de dominio para comprobar la traducción a ApiError. */
private class ThrowingRoutes : DomainRoutes {
    override fun Route.v1() {
        get("/_test/quota") { throw QuotaExceeded(Instant.parse("2026-10-26T05:00:00Z"), QuotaKind.SIMULATION) }
        get("/_test/not-found") { throw NotFound() }
        get("/_test/conflict") { throw Conflict("ya existe") }
        get("/_test/validation") { throw ValidationFailed("campo inválido") }
        get("/_test/budget") { throw AiBudgetExceeded(Instant.parse("2026-10-26T05:00:00Z")) }
        get("/_test/boom") { error("detalle interno que no debe filtrarse") }
        // Errores de cliente propios de Ktor (issue #49).
        post("/_test/eco") { call.respond(call.receive<Eco>()) }
        get("/_test/ktor-not-found") { throw NotFoundException("Plan inexistente") }
        get("/_test/payload") { throw PayloadTooLargeException(1024) }
        get("/_test/media") { throw UnsupportedMediaTypeException(ContentType.Text.Plain) }
        get("/_test/404-propio") { call.respond(HttpStatusCode.NotFound, ApiError(ErrorCode.NOT_FOUND, "Simulación inexistente")) }
        get("/_test/404-vacio") { call.respond(HttpStatusCode.NotFound) }
    }
}

@Serializable
private data class Eco(val texto: String)

private fun bearer(token: String): io.ktor.client.request.HttpRequestBuilder.() -> Unit =
    { header(HttpHeaders.Authorization, "Bearer $token") }

class ApplicationTest : FunSpec({
    val fakeToken = FakeTokenVerifier.tokenFor("ana")

    test("GET /v1/health responde 200 con la versión y sin token") {
        testApplication {
            val client = startApp()
            val response = client.get("/v1/health")
            response.status shouldBe HttpStatusCode.OK
            val health = response.body<Health>()
            health.version shouldBe "abc1234"
            health.time shouldBe Instant.parse("2026-10-25T12:00:00Z")
        }
    }

    test("GET /v1/me sin token responde 401 UNAUTHENTICATED") {
        testApplication {
            val response = startApp().get("/v1/me")
            response.status shouldBe HttpStatusCode.Unauthorized
            response.body<ApiError>().code shouldBe ErrorCode.UNAUTHENTICATED
        }
    }

    test("GET /v1/me con token inválido responde 401") {
        testApplication {
            startApp().get("/v1/me", bearer("no-es-un-token")).status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("GET /v1/me con el token Fake no es 401") {
        testApplication {
            val response = startApp().get("/v1/me", bearer(fakeToken))
            response.status shouldNotBe HttpStatusCode.Unauthorized
            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("QuotaExceeded produce 402 con ApiError QUOTA_EXCEEDED y resetsAt") {
        testApplication {
            val response = startApp(testContainer(listOf(ThrowingRoutes()))).get("/v1/_test/quota", bearer(fakeToken))
            response.status shouldBe HttpStatusCode.PaymentRequired
            val error = response.body<ApiError>()
            error.code shouldBe ErrorCode.QUOTA_EXCEEDED
            error.kind shouldBe "SIMULATION"
            error.resetsAt shouldBe Instant.parse("2026-10-26T05:00:00Z")
        }
    }

    test("Cada excepción de dominio usa el status de su código") {
        testApplication {
            val client = startApp(testContainer(listOf(ThrowingRoutes())))
            suspend fun check(path: String, status: HttpStatusCode, code: ErrorCode) {
                val response: HttpResponse = client.get("/v1/_test/$path", bearer(fakeToken))
                response.status shouldBe status
                response.body<ApiError>().code shouldBe code
            }
            check("not-found", HttpStatusCode.NotFound, ErrorCode.NOT_FOUND)
            check("conflict", HttpStatusCode.Conflict, ErrorCode.CONFLICT)
            check("validation", HttpStatusCode.BadRequest, ErrorCode.VALIDATION)
            check("budget", HttpStatusCode.ServiceUnavailable, ErrorCode.AI_BUDGET)
        }
    }

    test("Un error inesperado responde 500 sin filtrar el detalle") {
        testApplication {
            val response = startApp(testContainer(listOf(ThrowingRoutes()))).get("/v1/_test/boom", bearer(fakeToken))
            response.status shouldBe HttpStatusCode.InternalServerError
            val text = response.body<String>()
            (text.contains("detalle interno")) shouldBe false
        }
    }

    test("POST con text/plain a una ruta que hace receive responde 415 VALIDATION, no 500") {
        testApplication {
            val client = startApp(testContainer(listOf(ThrowingRoutes())))
            val response = client.post("/v1/_test/eco") {
                bearer(fakeToken)()
                contentType(ContentType.Text.Plain)
                setBody("hola")
            }
            response.status shouldBe HttpStatusCode.UnsupportedMediaType
            response.body<ApiError>().code shouldBe ErrorCode.VALIDATION
            // UnsupportedMediaTypeException lanzada directamente también es 415.
            val lanzada = client.get("/v1/_test/media", bearer(fakeToken))
            lanzada.status shouldBe HttpStatusCode.UnsupportedMediaType
            lanzada.body<ApiError>().code shouldBe ErrorCode.VALIDATION
        }
    }

    test("POST sin cuerpo a una ruta que hace receive responde 400 VALIDATION, no 500") {
        testApplication {
            val client = startApp(testContainer(listOf(ThrowingRoutes())))
            val sinCuerpo = client.post("/v1/_test/eco") { bearer(fakeToken)() }
            sinCuerpo.status shouldBe HttpStatusCode.BadRequest
            sinCuerpo.body<ApiError>().code shouldBe ErrorCode.VALIDATION
            val jsonVacio = client.post("/v1/_test/eco") {
                bearer(fakeToken)()
                contentType(ContentType.Application.Json)
            }
            jsonVacio.status shouldBe HttpStatusCode.BadRequest
            jsonVacio.body<ApiError>().code shouldBe ErrorCode.VALIDATION
        }
    }

    test("un cuerpo de más de 64 KB en /v1 responde 413 VALIDATION; uno normal pasa (B-B4)") {
        testApplication {
            val client = startApp(testContainer(listOf(ThrowingRoutes())))
            val grande = client.post("/v1/_test/eco") {
                bearer(fakeToken)()
                contentType(ContentType.Application.Json)
                setBody("""{"texto":"${"x".repeat(70 * 1024)}"}""")
            }
            grande.status shouldBe HttpStatusCode.PayloadTooLarge
            grande.body<ApiError>().code shouldBe ErrorCode.VALIDATION
            val normal = client.post("/v1/_test/eco") {
                bearer(fakeToken)()
                contentType(ContentType.Application.Json)
                setBody("""{"texto":"${"x".repeat(10 * 1024)}"}""")
            }
            normal.status shouldBe HttpStatusCode.OK
        }
    }

    test("NotFoundException de Ktor responde 404 NOT_FOUND con su mensaje") {
        testApplication {
            val response = startApp(testContainer(listOf(ThrowingRoutes()))).get("/v1/_test/ktor-not-found", bearer(fakeToken))
            response.status shouldBe HttpStatusCode.NotFound
            val error = response.body<ApiError>()
            error.code shouldBe ErrorCode.NOT_FOUND
            error.message shouldBe "Plan inexistente"
        }
    }

    test("PayloadTooLargeException responde 413 VALIDATION") {
        testApplication {
            val response = startApp(testContainer(listOf(ThrowingRoutes()))).get("/v1/_test/payload", bearer(fakeToken))
            response.status shouldBe HttpStatusCode.PayloadTooLarge
            response.body<ApiError>().code shouldBe ErrorCode.VALIDATION
        }
    }

    test("Un 404 que la ruta responde con su ApiError no se reescribe") {
        testApplication {
            val client = startApp(testContainer(listOf(ThrowingRoutes())))
            val propio = client.get("/v1/_test/404-propio", bearer(fakeToken))
            propio.status shouldBe HttpStatusCode.NotFound
            propio.body<ApiError>() shouldBe ApiError(ErrorCode.NOT_FOUND, "Simulación inexistente")
            val dominio = client.get("/v1/_test/not-found", bearer(fakeToken)).body<ApiError>()
            dominio.message shouldBe "No existe o no es visible"
            // Un 404 sin cuerpo sí se convierte en ApiError.
            val vacio = client.get("/v1/_test/404-vacio", bearer(fakeToken))
            vacio.status shouldBe HttpStatusCode.NotFound
            vacio.body<ApiError>().code shouldBe ErrorCode.NOT_FOUND
        }
    }

    test("Una ruta inexistente responde 404 NOT_FOUND") {
        testApplication {
            val response = startApp().get("/v1/no-existe", bearer(fakeToken))
            response.status shouldBe HttpStatusCode.NotFound
            response.body<ApiError>().code shouldBe ErrorCode.NOT_FOUND
        }
    }

    test("POST /internal/tasks/simulate sin token OIDC responde 403") {
        testApplication {
            val response = startApp().post("/internal/tasks/simulate")
            response.status shouldBe HttpStatusCode.Forbidden
            response.body<ApiError>().code shouldBe ErrorCode.FORBIDDEN
        }
    }

    test("POST /internal/tasks/simulate con token inválido o de usuario responde 403") {
        testApplication {
            val client = startApp()
            client.post("/internal/tasks/simulate", bearer("basura")).status shouldBe HttpStatusCode.Forbidden
            // Un ID token de usuario de Firebase no sirve en /internal.
            client.post("/internal/tasks/simulate", bearer(fakeToken)).status shouldBe HttpStatusCode.Forbidden
        }
    }

    // E4.3 implementó /internal/tasks/simulate (SimulationApiTest) y E4.8 judge-scenario (ScenarioApiTest).
    test("POST /internal/tasks/judge-scenario exige OIDC: sin token es 403 y con el token de la cuenta de tareas no") {
        testApplication {
            val client = startApp()
            client.post("/internal/tasks/judge-scenario").status shouldBe HttpStatusCode.Forbidden
            client.post("/internal/tasks/judge-scenario", bearer(FakeOidcVerifier.VALID_TOKEN)).status shouldNotBe HttpStatusCode.Forbidden
        }
    }

    test("El token OIDC no sirve en /v1") {
        testApplication {
            startApp().get("/v1/me", bearer(FakeOidcVerifier.VALID_TOKEN)).status shouldBe HttpStatusCode.Unauthorized
        }
    }
})
