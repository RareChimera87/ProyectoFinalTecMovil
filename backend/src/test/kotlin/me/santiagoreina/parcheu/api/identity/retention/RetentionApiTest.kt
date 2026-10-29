package me.santiagoreina.parcheu.api.identity.retention

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.TEST_OIDC_TOKEN
import me.santiagoreina.parcheu.api.graph.inMemoryGraphModule
import me.santiagoreina.parcheu.api.identity.deletion.inMemoryAccountModule
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.startApp
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.twin.inMemoryTwinModule
import java.time.Instant

/** `POST /internal/jobs/retention`: exige OIDC, responde 204 y aplica las reglas (E8.8). */
class RetentionApiTest : FunSpec({
    class Repo : RetentionRepository by NoopRetentionRepository {
        var cutoff: Instant? = null

        override suspend fun deleteNotificationsBefore(cutoff: Instant): Int {
            this.cutoff = cutoff
            return 2
        }

        override suspend fun deleteExpiredMatchesBefore(cutoff: Instant): Int = error("Postgres caído")
    }

    fun container(repo: RetentionRepository) = run {
        val graph = inMemoryGraphModule(FIXED_CLOCK)
        val twin = inMemoryTwinModule(FIXED_CLOCK, testGateway())
        testContainer(graph = graph, twin = twin) { identity ->
            inMemoryAccountModule(identity, graph, twin, FIXED_CLOCK, retentionRepository = repo)
        }
    }

    test("sin token OIDC la ruta responde 403 y con un token de usuario de Firebase también") {
        testApplication {
            val client = startApp(container(Repo()))
            client.post("/internal/jobs/retention").status shouldBe HttpStatusCode.Forbidden
            client.post("/internal/jobs/retention") {
                header(HttpHeaders.Authorization, "Bearer ${FakeTokenVerifier.tokenFor("ana")}")
            }.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("con OIDC responde 204 y aplica los plazos con el reloj del contenedor; una regla que falla da 500 sin impedir las demás") {
        val repo = Repo()
        testApplication {
            val client = startApp(container(repo))
            val status = client.post("/internal/jobs/retention") {
                header(HttpHeaders.Authorization, "Bearer ${TEST_OIDC_TOKEN}")
            }.status
            // `deleteExpiredMatchesBefore` falla: 500, pero la regla de notificaciones ya se aplicó.
            status shouldBe HttpStatusCode.InternalServerError
            repo.cutoff shouldBe FIXED_CLOCK.instant().minusSeconds(90L * 86400)
        }
    }

    test("sin fallos responde 204") {
        testApplication {
            val client = startApp(container(NoopRetentionRepository))
            client.post("/internal/jobs/retention") {
                header(HttpHeaders.Authorization, "Bearer ${TEST_OIDC_TOKEN}")
            }.status shouldBe HttpStatusCode.NoContent
        }
    }
})
