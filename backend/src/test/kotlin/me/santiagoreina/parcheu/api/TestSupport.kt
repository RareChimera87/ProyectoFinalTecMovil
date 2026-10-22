package me.santiagoreina.parcheu.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import me.santiagoreina.parcheu.api.identity.IdentityService
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.identity.profile.InMemoryProfileRepository
import me.santiagoreina.parcheu.api.identity.profile.ProfileService
import me.santiagoreina.parcheu.api.platform.AppContainer
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.allDomains
import me.santiagoreina.parcheu.api.platform.auth.FakeOidcVerifier
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.platform.config.AppConfig
import me.santiagoreina.parcheu.api.platform.config.Env
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

const val TEST_LEGAL_VERSION = "2026-10-29"

/**
 * Contenedor con Fakes. El usuario `ana` ya existe con términos aceptados, así que las pruebas de
 * otros dominios pasan la puerta de `TERMS_REQUIRED` sin repetir el alta; para probar la sesión
 * se pasa un repositorio propio.
 */
fun testContainer(
    extra: List<DomainRoutes> = emptyList(),
    repo: InMemoryIdentityRepository = InMemoryIdentityRepository().also { it.seedAcceptedUser("ana", TEST_LEGAL_VERSION) },
    legalTextVersion: String = TEST_LEGAL_VERSION,
    clock: Clock = FIXED_CLOCK,
): AppContainer {
    val identity = IdentityService(repo, clock, legalTextVersion)
    val profile = ProfileService(identity, InMemoryProfileRepository(repo), clock)
    return AppContainer(
        config = testConfig(),
        clock = clock,
        tokenVerifier = FakeTokenVerifier(),
        oidcVerifier = FakeOidcVerifier(TASKS_ACCOUNT),
        identity = identity,
        profile = profile,
        domains = allDomains(identity, profile) + extra,
    )
}

/** Arranca el módulo con Fakes y devuelve un cliente que entiende el JSON del contrato. */
fun ApplicationTestBuilder.startApp(container: AppContainer = testContainer()): HttpClient {
    application { module(container) }
    return createClient { install(ContentNegotiation) { json(ContractJson) } }
}
