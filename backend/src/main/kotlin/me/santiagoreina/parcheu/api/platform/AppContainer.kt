package me.santiagoreina.parcheu.api.platform

import me.santiagoreina.parcheu.api.admin.AdminRoutes
import me.santiagoreina.parcheu.api.ai.AiConfig
import me.santiagoreina.parcheu.api.ai.AiGateway
import me.santiagoreina.parcheu.api.ai.AiModule
import me.santiagoreina.parcheu.api.ai.ExposedAiUsageStore
import me.santiagoreina.parcheu.api.ai.FakeLlmClient
import me.santiagoreina.parcheu.api.ai.InMemoryAiUsageStore
import me.santiagoreina.parcheu.api.billing.BillingRoutes
import me.santiagoreina.parcheu.api.chat.ChatRoutes
import me.santiagoreina.parcheu.api.graph.GraphRoutes
import me.santiagoreina.parcheu.api.home.HomeRoutes
import me.santiagoreina.parcheu.api.identity.ExposedIdentityRepository
import me.santiagoreina.parcheu.api.identity.IdentityRoutes
import me.santiagoreina.parcheu.api.identity.IdentityService
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
import org.jetbrains.exposed.v1.jdbc.Database
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
    val identity: IdentityService,
    /** Cuotas (E4.1). Por defecto en memoria: solo [fromConfig] cablea Postgres. */
    val quota: QuotaModule = inMemoryQuotaModule(clock),
    /** Dominios con rutas. Los tests pueden agregar rutas propias; el orden no importa. */
    val domains: List<DomainRoutes> = allDomains(identity, quota),
    /** Pasarela de IA (E0.9). Por defecto Fake con uso en memoria (tests y `ENV=local`). */
    val ai: AiGateway = localAiGateway(clock),
) {
    companion object {
        /**
         * Cableado de producción: verificadores reales (Fake solo con `ENV=local`). La
         * configuración de IA se valida aquí, así que un `AI_PROVIDER` inválido hace fallar el
         * arranque (E0.9).
         */
        fun fromConfig(config: AppConfig, vars: Map<String, String> = System.getenv()): AppContainer {
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
            val dbConfig = requireNotNull(config.db) {
                "El servicio necesita la base de datos: define DB_NAME y CLOUD_SQL_INSTANCE (o DB_URL en local)"
            }
            val database = connectExposed(createDataSource(dbConfig, failFast = false))
            val identity = IdentityService(ExposedIdentityRepository(database), clock, config.legalTextVersion)
            val quota = quotaModule(config.env, database, clock)
            val ai = aiGateway(config, vars, clock) { database }
            return AppContainer(config, clock, tokenVerifier, oidcVerifier, identity, quota, ai = ai)
        }

        /**
         * Pasarela de IA: uso en Postgres si hay base configurada (reutiliza [database] si se pasa;
         * si no, abre un pool perezoso propio); en memoria solo con `ENV=local`.
         */
        fun aiGateway(
            config: AppConfig,
            vars: Map<String, String>,
            clock: Clock,
            database: (() -> Database)? = null,
        ): AiGateway {
            val aiConfig = AiConfig.fromEnv(vars, config.env)
            val db = config.db
            val usage = when {
                database != null -> ExposedAiUsageStore(database)
                db != null -> ExposedAiUsageStore { connectExposed(createDataSource(db, maxPoolSize = 2)) }
                config.env == Env.LOCAL -> InMemoryAiUsageStore()
                else -> error("La pasarela de IA necesita DB_NAME y la conexión a la base en ${config.env}")
            }
            return AiModule.gateway(aiConfig, usage, clock)
        }

        private fun localAiGateway(clock: Clock): AiGateway {
            val config = AiConfig.fromEnv(emptyMap(), Env.LOCAL)
            return AiModule.gateway(config, InMemoryAiUsageStore(), clock, FakeLlmClient())
        }
    }
}

/** Todos los dominios de arquitectura.md §3.1 que exponen rutas. */
fun allDomains(identity: IdentityService, quota: QuotaModule): List<DomainRoutes> = listOf(
    IdentityRoutes(identity),
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
