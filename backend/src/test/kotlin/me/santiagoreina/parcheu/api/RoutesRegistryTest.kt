package me.santiagoreina.parcheu.api

import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import me.santiagoreina.parcheu.api.platform.auth.FakeOidcVerifier
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier

/**
 * Rutas de arquitectura.md §5.2, agrupadas por dominio. Mientras el dominio sea un stub todas
 * responden 501; cuando una feature implementa su dominio, ajusta el estado esperado de sus rutas
 * (o las quita de esta lista) en su propio PR. Quitadas: `GET /v1/quota` y `POST /internal/dev/grant-plus`
 * (E4.1, probadas en `QuotaApiTest`).
 */
private val v1Routes: Map<String, List<Pair<HttpMethod, String>>> = mapOf(
    // Identity: solo los stubs que quedan; las rutas implementadas por E2.1 las cubre IdentityApiTest.
    "identity" to listOf(
        HttpMethod.Get to "/v1/catalog", HttpMethod.Delete to "/v1/me", HttpMethod.Get to "/v1/me/export",
        HttpMethod.Put to "/v1/me/modes", HttpMethod.Post to "/v1/me/age-verification",
        HttpMethod.Get to "/v1/me/privacy", HttpMethod.Patch to "/v1/me/privacy",
    ),
    // E2.3 implementó questions, answers, twin y evolution (ver TwinApiTest); el resto sigue en 501.
    "twin" to listOf(
        HttpMethod.Get to "/v1/twin/memories",
        HttpMethod.Post to "/v1/twin/memories/m1/accept", HttpMethod.Post to "/v1/twin/memories/m1/reject",
        HttpMethod.Get to "/v1/twin/feedback-prompts", HttpMethod.Post to "/v1/twin/feedback",
    ),
    "matching" to listOf(
        HttpMethod.Post to "/v1/match/queue", HttpMethod.Get to "/v1/match/queue", HttpMethod.Delete to "/v1/match/queue",
        HttpMethod.Get to "/v1/matches/m1", HttpMethod.Post to "/v1/matches/m1/choice",
    ),
    "simulation" to listOf(
        HttpMethod.Post to "/v1/simulations", HttpMethod.Get to "/v1/simulations", HttpMethod.Get to "/v1/simulations/s1",
        HttpMethod.Get to "/v1/simulations/s1/report", HttpMethod.Post to "/v1/simulations/s1/report:unlock",
        HttpMethod.Get to "/v1/simulations/s1/transcript", HttpMethod.Post to "/v1/simulations/s1/decision",
    ),
    "scenario" to listOf(HttpMethod.Get to "/v1/scenarios", HttpMethod.Post to "/v1/scenario-sessions/c1/agreement"),
    "graph" to listOf(
        // GET /v1/graph y /v1/graph/nodes/{id} los implementa E3.1 (GraphApiTest).
        HttpMethod.Get to "/v1/graph/fof",
        HttpMethod.Get to "/v1/graph/history", HttpMethod.Get to "/v1/communities", HttpMethod.Get to "/v1/connect-requests",
    ),
    "social" to listOf(
        HttpMethod.Post to "/v1/introductions", HttpMethod.Get to "/v1/introductions/i1",
        HttpMethod.Post to "/v1/introductions/i1/response", HttpMethod.Post to "/v1/introductions/request",
        HttpMethod.Get to "/v1/invites/me", HttpMethod.Post to "/v1/invites/redeem", HttpMethod.Post to "/v1/invites/u1/accept",
    ),
    "chat" to listOf(
        HttpMethod.Get to "/v1/chats", HttpMethod.Post to "/v1/chats/c1/messages",
        HttpMethod.Post to "/v1/chats/c1/read", HttpMethod.Post to "/v1/chats/c1/mute",
    ),
    "notifications" to listOf(HttpMethod.Get to "/v1/notifications", HttpMethod.Post to "/v1/notifications/read"),
    "billing" to listOf(HttpMethod.Get to "/v1/billing/entitlement", HttpMethod.Post to "/v1/billing/verify"),
    "safety" to listOf(
        HttpMethod.Post to "/v1/reports", HttpMethod.Get to "/v1/blocks",
        HttpMethod.Post to "/v1/blocks/u1", HttpMethod.Delete to "/v1/blocks/u1",
    ),
    "admin" to listOf(HttpMethod.Get to "/v1/admin/reports", HttpMethod.Post to "/v1/admin/reports/r1/resolve"),
    "home" to listOf(HttpMethod.Get to "/v1/home"),
)

private val internalRoutes = listOf(
    "/internal/tasks/simulate", "/internal/tasks/judge-scenario",
    "/internal/tasks/extract-memories", "/internal/tasks/moderate", "/internal/tasks/name-community",
    "/internal/tasks/purge-user", "/internal/jobs/communities", "/internal/jobs/memories",
    "/internal/jobs/twin-accuracy", "/internal/jobs/retention", "/internal/billing/rtdn",
)

class RoutesRegistryTest : FunSpec({
    fun HttpRequestBuilder.auth(token: String) = header(HttpHeaders.Authorization, "Bearer $token")

    v1Routes.forEach { (domain, routes) ->
        test("el dominio $domain registra todas sus rutas /v1 (501 mientras sea stub)") {
            testApplication {
                val client = startApp()
                routes.forEach { (method, path) ->
                    val status = client.request(path) {
                        this.method = method
                        auth(FakeTokenVerifier.tokenFor("ana"))
                    }.status
                    withClue("$method $path") { status shouldBe HttpStatusCode.NotImplemented }
                }
            }
        }
    }

    test("todas las rutas /internal están registradas y exigen OIDC") {
        testApplication {
            val client = startApp()
            internalRoutes.forEach { path ->
                val sinToken = client.request(path) { method = HttpMethod.Post }.status
                withClue("sin token $path") { sinToken shouldBe HttpStatusCode.Forbidden }
                val conToken = client.request(path) {
                    method = HttpMethod.Post
                    auth(FakeOidcVerifier.VALID_TOKEN)
                }.status
                withClue("con token $path") { conToken shouldBe HttpStatusCode.NotImplemented }
            }
        }
    }
})
