package me.santiagoreina.parcheu.api.platform

import me.santiagoreina.parcheu.api.admin.AdminRoutes
import me.santiagoreina.parcheu.api.billing.BillingRoutes
import me.santiagoreina.parcheu.api.chat.ChatRoutes
import me.santiagoreina.parcheu.api.graph.GraphRoutes
import me.santiagoreina.parcheu.api.home.HomeRoutes
import me.santiagoreina.parcheu.api.identity.IdentityRoutes
import me.santiagoreina.parcheu.api.matching.MatchingRoutes
import me.santiagoreina.parcheu.api.notifications.NotificationsRoutes
import me.santiagoreina.parcheu.api.platform.auth.FakeOidcVerifier
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.platform.auth.FirebaseTokenVerifier
import me.santiagoreina.parcheu.api.platform.auth.GoogleOidcVerifier
import me.santiagoreina.parcheu.api.platform.auth.OidcVerifier
import me.santiagoreina.parcheu.api.platform.auth.TokenVerifier
import me.santiagoreina.parcheu.api.platform.config.AppConfig
import me.santiagoreina.parcheu.api.platform.config.Env
import me.santiagoreina.parcheu.api.platform.db.connectExposed
import me.santiagoreina.parcheu.api.platform.db.createDataSource
import me.santiagoreina.parcheu.api.quota.QuotaModule
import me.santiagoreina.parcheu.api.quota.QuotaRoutes
import me.santiagoreina.parcheu.api.quota.inMemoryQuotaModule
import me.santiagoreina.parcheu.api.quota.quotaModule
import me.santiagoreina.parcheu.api.safety.SafetyRoutes
import me.santiagoreina.parcheu.api.scenario.ScenarioRoutes
import me.santiagoreina.parcheu.api.simulation.SimulationRoutes
import me.santiagoreina.parcheu.api.social.SocialRoutes
import me.santiagoreina.parcheu.api.twin.TwinRoutes
import java.time.Clock

/**
 * Inyección de dependencias manual por constructores (arquitectura.md §3.2). Aquí se construyen
 * los servicios y repositorios y se pasan a las rutas de cada dominio: las features agregan sus
 * líneas en este archivo y en su `XRoutes.kt`, nunca en `Application.kt`.
 */
class AppContainer(
    val config: AppConfig,
    val clock: Clock,
    val tokenVerifier: TokenVerifier,
    val oidcVerifier: OidcVerifier,
    /** Cuotas (E4.1). Por defecto en memoria: solo [fromConfig] cablea Postgres. */
    val quota: QuotaModule = inMemoryQuotaModule(clock),
    /** Dominios con rutas. Los tests pueden agregar rutas propias; el orden no importa. */
    val domains: List<DomainRoutes> = allDomains(quota),
) {
    companion object {
        /** Cableado de producción: verificadores reales (Fake solo con `ENV=local`). */
        fun fromConfig(config: AppConfig): AppContainer {
            val local = config.env == Env.LOCAL
            val tokenVerifier = if (local) {
                FakeTokenVerifier()
            } else {
                FirebaseTokenVerifier(requireNotNull(config.firebaseProjectId))
            }
            val oidcVerifier = if (local) {
                FakeOidcVerifier(config.tasksServiceAccount)
            } else {
                GoogleOidcVerifier(config.oidcAudience, config.tasksServiceAccount)
            }
            val clock = systemClock()
            // Pool perezoso (failFast = false): el servicio arranca aunque Cloud SQL tarde en responder.
            val database = config.db?.let { connectExposed(createDataSource(it, failFast = false)) }
            val quota = quotaModule(config.env, database, clock)
            return AppContainer(config, clock, tokenVerifier, oidcVerifier, quota)
        }
    }
}

/** Todos los dominios de arquitectura.md §3.1 que exponen rutas. */
fun allDomains(quota: QuotaModule): List<DomainRoutes> = listOf(
    IdentityRoutes(),
    TwinRoutes(),
    QuotaRoutes(quota),
    MatchingRoutes(),
    SimulationRoutes(),
    ScenarioRoutes(),
    GraphRoutes(),
    SocialRoutes(),
    ChatRoutes(),
    NotificationsRoutes(),
    BillingRoutes(),
    SafetyRoutes(),
    AdminRoutes(),
    HomeRoutes(),
)
