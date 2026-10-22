package me.santiagoreina.parcheu.api.platform

import me.santiagoreina.parcheu.api.admin.AdminRoutes
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
import me.santiagoreina.parcheu.api.quota.QuotaRoutes
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
    val identity: IdentityService,
    /** Grafo (E3.1). Por defecto en memoria: solo [fromConfig] cablea Postgres y la clave de `anonId`. */
    val graph: GraphModule = inMemoryGraphModule(clock),
    /** Chat (E5.1). Por defecto en memoria (repositorio y espejo): solo [fromConfig] cablea Postgres y Firestore. */
    val chat: ChatModule = inMemoryChatModule(clock, graph.policy),
    /** Dominios con rutas. Los tests pueden agregar rutas propias; el orden no importa. */
    val domains: List<DomainRoutes> = allDomains(identity, graph, chat),
    /** Avisos (push + bandeja). Los tests de otros dominios pasan un `FakeNotifier`. */
    val notifier: Notifier = NoopNotifier,
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
            val dbConfig = requireNotNull(config.db) {
                "El servicio necesita la base de datos: define DB_NAME y CLOUD_SQL_INSTANCE (o DB_URL en local)"
            }
            val database = connectExposed(createDataSource(dbConfig, failFast = false))
            val identity = IdentityService(ExposedIdentityRepository(database), clock, config.legalTextVersion)
            val graph = GraphModule(ExposedGraphRepository(database), AnonIdSigner.fromEnv(config.env), clock)
            // E5.3 (PR #30) cambia este valor por FcmNotifier; hasta entonces los avisos del chat no salen.
            val notifier: Notifier = NoopNotifier
            // En local no hay Firestore: el espejo del chat queda en memoria (Postgres sigue siendo la verdad).
            val chatMirror = if (local) InMemoryChatMirror() else FirestoreChatMirror.forProject(requireNotNull(config.firebaseProjectId))
            val chat = ChatModule(ExposedChatRepository(database), chatMirror, notifier, graph.policy, clock)
            return AppContainer(config, clock, tokenVerifier, oidcVerifier, identity, graph, chat, notifier = notifier)
        }
    }
}

/** Todos los dominios de arquitectura.md §3.1 que exponen rutas. */
fun allDomains(
    identity: IdentityService,
    graph: GraphModule = inMemoryGraphModule(systemClock()),
    chat: ChatModule = inMemoryChatModule(systemClock(), graph.policy),
): List<DomainRoutes> = listOf(
    IdentityRoutes(identity),
    TwinRoutes(),
    QuotaRoutes(),
    MatchingRoutes(),
    SimulationRoutes(),
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
