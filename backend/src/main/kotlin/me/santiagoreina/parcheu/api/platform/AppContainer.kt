package me.santiagoreina.parcheu.api.platform

import me.santiagoreina.parcheu.api.admin.AdminRoutes
import me.santiagoreina.parcheu.api.ai.AiConfig
import me.santiagoreina.parcheu.api.ai.AiGateway
import me.santiagoreina.parcheu.api.ai.AiModule
import me.santiagoreina.parcheu.api.ai.ExposedAiUsageStore
import me.santiagoreina.parcheu.api.ai.FakeLlmClient
import me.santiagoreina.parcheu.api.ai.InMemoryAiUsageStore
import me.santiagoreina.parcheu.api.billing.BillingRoutes
import me.santiagoreina.parcheu.api.chat.ChatModule
import me.santiagoreina.parcheu.api.chat.ChatRoutes
import me.santiagoreina.parcheu.api.chat.ExposedChatRepository
import me.santiagoreina.parcheu.api.chat.FirestoreChatMirror
import me.santiagoreina.parcheu.api.chat.InMemoryChatMirror
import me.santiagoreina.parcheu.api.chat.inMemoryChatModule
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.ExposedGraphRepository
import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.graph.GraphRoutes
import me.santiagoreina.parcheu.api.graph.inMemoryGraphModule
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
import me.santiagoreina.parcheu.api.platform.notifier.NoopNotifier
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import me.santiagoreina.parcheu.api.quota.QuotaModule
import me.santiagoreina.parcheu.api.quota.QuotaRoutes
import me.santiagoreina.parcheu.api.quota.inMemoryQuotaModule
import me.santiagoreina.parcheu.api.quota.quotaModule
import me.santiagoreina.parcheu.api.safety.SafetyRoutes
import me.santiagoreina.parcheu.api.scenario.ScenarioRoutes
import me.santiagoreina.parcheu.api.simulation.SimulationModule
import me.santiagoreina.parcheu.api.simulation.SimulationRoutes
import me.santiagoreina.parcheu.api.simulation.inMemorySimulationModule
import me.santiagoreina.parcheu.api.simulation.simulationModule
import me.santiagoreina.parcheu.api.social.SocialRoutes
import me.santiagoreina.parcheu.api.platform.tasks.CloudTasksQueue
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.twin.TwinModule
import me.santiagoreina.parcheu.api.twin.TwinRoutes
import me.santiagoreina.parcheu.api.twin.inMemoryTwinModule
import me.santiagoreina.parcheu.api.twin.twinModule
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
    /** Pasarela de IA (E0.9). Por defecto Fake con uso en memoria (tests y `ENV=local`). */
    val ai: AiGateway = localAiGateway(clock),
    /** Gemelo (E2.3). Por defecto en memoria: solo [fromConfig] cablea Postgres y Cloud Tasks. */
    val twin: TwinModule = inMemoryTwinModule(clock, ai),
    /** Cuotas (E4.1). Por defecto en memoria: solo [fromConfig] cablea Postgres. */
    val quota: QuotaModule = inMemoryQuotaModule(clock),
    /** Grafo (E3.1). Por defecto en memoria: solo [fromConfig] cablea Postgres y la clave de `anonId`. */
    val graph: GraphModule = inMemoryGraphModule(clock),
    /** Chat (E5.1). Por defecto en memoria (repositorio y espejo): solo [fromConfig] cablea Postgres y Firestore. */
    val chat: ChatModule = inMemoryChatModule(clock, graph.policy),
    /** Simulación (E4.3). Por defecto en memoria: solo [fromConfig] cablea Postgres, Cloud Tasks y Firestore. */
    val simulation: SimulationModule = inMemorySimulationModule(clock, ai, quota, graph),
    /** Dominios con rutas. Los tests pueden agregar rutas propias; el orden no importa. */
    val domains: List<DomainRoutes> = allDomains(identity, twin, quota, graph, simulation, chat),
    /** Avisos (push + bandeja). Los tests de otros dominios pasan un `FakeNotifier`. */
    val notifier: Notifier = NoopNotifier,
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
            val ai = aiGateway(config, vars, clock)
            val dbConfig = requireNotNull(config.db) {
                "El servicio necesita la base de datos: define DB_NAME y CLOUD_SQL_INSTANCE (o DB_URL en local)"
            }
            // Pool perezoso (failFast = false): el servicio arranca aunque Cloud SQL tarde en responder.
            val database = connectExposed(createDataSource(dbConfig, failFast = false))
            val tasks = if (local) FakeTaskQueue() else CloudTasksQueue.fromEnv(config, vars)
            val identity = IdentityService(ExposedIdentityRepository(database), clock, config.legalTextVersion)
            val twin = twinModule(config.env, database, clock, ai, tasks)
            val quota = quotaModule(config.env, database, clock)
            val graph = GraphModule(ExposedGraphRepository(database), AnonIdSigner.fromEnv(config.env), clock)
            val simulation = simulationModule(config.env, database, clock, ai, quota, graph, tasks, config.firebaseProjectId)
            // E5.3 (PR #30) cambia este valor por FcmNotifier; hasta entonces los avisos del chat no salen.
            val notifier: Notifier = NoopNotifier
            // En local no hay Firestore: el espejo del chat queda en memoria (Postgres sigue siendo la verdad).
            val chatMirror = if (local) InMemoryChatMirror() else FirestoreChatMirror.forProject(requireNotNull(config.firebaseProjectId))
            val chat = ChatModule(ExposedChatRepository(database), chatMirror, notifier, graph.policy, clock)
            return AppContainer(
                config, clock, tokenVerifier, oidcVerifier, identity,
                ai = ai, twin = twin, quota = quota, graph = graph, chat = chat, simulation = simulation,
                notifier = notifier,
            )
        }

        /** Pasarela de IA: uso en Postgres si hay base configurada; en memoria solo con `ENV=local`. */
        fun aiGateway(config: AppConfig, vars: Map<String, String>, clock: Clock): AiGateway {
            val aiConfig = AiConfig.fromEnv(vars, config.env)
            val db = config.db
            val usage = when {
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
fun allDomains(
    identity: IdentityService,
    twin: TwinModule,
    quota: QuotaModule,
    graph: GraphModule,
    simulation: SimulationModule,
    chat: ChatModule = inMemoryChatModule(systemClock(), graph.policy),
): List<DomainRoutes> = listOf(
    IdentityRoutes(identity),
    TwinRoutes(twin),
    QuotaRoutes(quota),
    MatchingRoutes(),
    SimulationRoutes(simulation) { firebase -> identity.requireUser(firebase).id },
    ScenarioRoutes(),
    GraphRoutes(graph.service) { firebase -> identity.requireUser(firebase).id },
    SocialRoutes(),
    ChatRoutes(chat.service) { firebase -> identity.requireUser(firebase) },
    NotificationsRoutes(),
    BillingRoutes(),
    SafetyRoutes(),
    AdminRoutes(),
    HomeRoutes(),
)
