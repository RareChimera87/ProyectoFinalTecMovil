package me.santiagoreina.parcheu.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import me.santiagoreina.parcheu.api.ai.AiGateway
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.platform.AppContainer
import me.santiagoreina.parcheu.api.twin.TwinModule
import me.santiagoreina.parcheu.api.twin.inMemoryTwinModule
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.allDomains
import me.santiagoreina.parcheu.api.platform.auth.FakeOidcVerifier
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.platform.config.AppConfig
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.quota.QuotaModule
import me.santiagoreina.parcheu.api.quota.inMemoryQuotaModule
import me.santiagoreina.parcheu.contract.ContractJson
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

const val TASKS_ACCOUNT = "sa-tasks@parcheu-test.iam.gserviceaccount.com"

val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC)

fun testConfig() = AppConfig(
    env = Env.LOCAL,
    port = 0,
    version = "abc1234",
    oidcAudience = "http://localhost",
    tasksServiceAccount = TASKS_ACCOUNT,
    firebaseProjectId = null,
    db = null,
)

fun testContainer(
    extra: List<DomainRoutes> = emptyList(),
    ai: AiGateway = testGateway(),
    twin: TwinModule = inMemoryTwinModule(FIXED_CLOCK, ai),
    quota: QuotaModule = inMemoryQuotaModule(FIXED_CLOCK),
    config: AppConfig = testConfig(),
) = AppContainer(
    config = config,
    clock = FIXED_CLOCK,
    tokenVerifier = FakeTokenVerifier(),
    oidcVerifier = FakeOidcVerifier(TASKS_ACCOUNT),
    ai = ai,
    twin = twin,
    quota = quota,
    domains = allDomains(twin, quota) + extra,
)

/** Arranca el módulo con Fakes y devuelve un cliente que entiende el JSON del contrato. */
fun ApplicationTestBuilder.startApp(container: AppContainer = testContainer()): HttpClient {
    application { module(container) }
    return createClient { install(ContentNegotiation) { json(ContractJson) } }
}
