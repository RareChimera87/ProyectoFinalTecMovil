package me.santiagoreina.parcheu.api.platform

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
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
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.TEST_OIDC_TOKEN
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import java.util.UUID

/**
 * B-M1: límite por persona (`AI_RATE_LIMIT_PER_MINUTE`, 30 por defecto) en las rutas `/v1` que disparan IA. La
 * clave es el UID del token: una persona limitada no afecta a otra, ni a las rutas sin IA ni a `/internal`.
 */
class AiRateLimitTest : FunSpec({
    suspend fun HttpClient.postAs(uid: String, path: String) = post(path) {
        header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor(uid)}")
        contentType(ContentType.Application.Json)
        // Cuerpo inválido a propósito: el límite cuenta la petición aunque el dominio la rechace, y no toca la IA.
        setBody("""{"items":[]}""")
    }

    test("pasadas 30 peticiones en el minuto la persona recibe 429 con Retry-After; otra persona, las rutas sin IA y /internal siguen") {
        testApplication {
            val client = startApp(
                testContainer(
                    repo = InMemoryIdentityRepository().also {
                        it.seedAcceptedUser("ana", TEST_LEGAL_VERSION)
                        it.seedAcceptedUser("bruno", TEST_LEGAL_VERSION)
                    },
                ),
            )
            repeat(DEFAULT_AI_RATE_LIMIT_PER_MINUTE) {
                client.postAs("ana", "/v1/twin/answers").status shouldBe HttpStatusCode.BadRequest
            }
            val limited = client.postAs("ana", "/v1/twin/answers")
            limited.status shouldBe HttpStatusCode.TooManyRequests
            limited.headers[HttpHeaders.RetryAfter]!!.toInt() shouldBeGreaterThan 0
            limited.bodyAsText() shouldContain "\"code\":\"RATE_LIMITED\""
            limited.bodyAsText() shouldContain "\"message\""
            // El límite es compartido entre las rutas que disparan IA de la misma persona.
            client.postAs("ana", "/v1/chats/${UUID.randomUUID()}/messages").status shouldBe HttpStatusCode.TooManyRequests
            client.postAs("ana", "/v1/simulations").status shouldBe HttpStatusCode.TooManyRequests

            // Otra persona tiene su propio contador.
            client.postAs("bruno", "/v1/twin/answers").status shouldBe HttpStatusCode.BadRequest
            // Las rutas sin IA no tienen límite.
            client.get("/v1/twin") { header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor("ana")}") }
                .status shouldBe HttpStatusCode.OK
            // /internal (Cloud Tasks, Scheduler) no pasa por el límite.
            repeat(DEFAULT_AI_RATE_LIMIT_PER_MINUTE + 5) {
                client.post("/internal/tasks/build-persona") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_OIDC_TOKEN}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"id":"${UUID.randomUUID()}"}""")
                }.status shouldNotBe HttpStatusCode.TooManyRequests
            }
        }
    }

    test("AI_RATE_LIMIT_PER_MINUTE: 30 por defecto, entero >= 1") {
        aiRateLimitPerMinute(emptyMap()) shouldBe 30
        aiRateLimitPerMinute(mapOf("AI_RATE_LIMIT_PER_MINUTE" to "120")) shouldBe 120
        shouldThrow<IllegalStateException> { aiRateLimitPerMinute(mapOf("AI_RATE_LIMIT_PER_MINUTE" to "0")) }
    }
})
