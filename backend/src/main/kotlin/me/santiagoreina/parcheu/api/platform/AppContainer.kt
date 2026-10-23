package me.santiagoreina.parcheu.api.platform

import me.santiagoreina.parcheu.api.admin.AdminRoutes
import me.santiagoreina.parcheu.api.ai.AiConfig
import me.santiagoreina.parcheu.api.ai.AiGateway
import me.santiagoreina.parcheu.api.ai.AiModule
import me.santiagoreina.parcheu.api.ai.ExposedAiUsageStore
import me.santiagoreina.parcheu.api.ai.FakeLlmClient
import me.santiagoreina.parcheu.api.ai.InMemoryAiUsageStore
import me.santiagoreina.parcheu.api.billing.BillingModule
import me.santiagoreina.parcheu.api.billing.BillingRoutes
import me.santiagoreina.parcheu.api.billing.billingModule
import me.santiagoreina.parcheu.api.billing.inMemoryBillingModule
import me.santiagoreina.parcheu.api.chat.ChatModule
import me.santiagoreina.parcheu.api.chat.ChatRoutes
import me.santiagoreina.parcheu.api.chat.ExposedChatRepository
import me.santiagoreina.parcheu.api.chat.FirestoreChatMirror
import me.santiagoreina.parcheu.api.chat.InMemoryChatMirror
import me.santiagoreina.parcheu.api.chat.inMemoryChatModule
import me.santiagoreina.parcheu.api.demo.DemoConnectPolicy
import me.santiagoreina.parcheu.api.demo.DemoSettings
import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.ExposedGraphRepository
import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.graph.GraphRoutes
import me.santiagoreina.parcheu.api.graph.fof.ExposedFofRepository
import me.santiagoreina.parcheu.api.graph.fof.FofService
import me.santiagoreina.parcheu.api.graph.fof.InMemoryFofRepository
import me.santiagoreina.parcheu.api.graph.inMemoryGraphModule
import me.santiagoreina.parcheu.api.home.HomeRoutes
import me.santiagoreina.parcheu.api.home.HomeService
import me.santiagoreina.parcheu.api.home.HomeSuggestions
import me.santiagoreina.parcheu.api.home.UnreadCounter
import me.santiagoreina.parcheu.api.identity.ExposedIdentityRepository
import me.santiagoreina.parcheu.api.identity.IdentityRoutes
import me.santiagoreina.parcheu.api.identity.IdentityService
import me.santiagoreina.parcheu.api.identity.deletion.AccountModule
import me.santiagoreina.parcheu.api.identity.deletion.accountModule
import me.santiagoreina.parcheu.api.identity.deletion.inMemoryAccountModule
import me.santiagoreina.parcheu.api.identity.profile.ExposedProfileRepository
import me.santiagoreina.parcheu.api.identity.profile.ProfileService
import me.santiagoreina.parcheu.api.matching.MatchingFlags
import me.santiagoreina.parcheu.api.matching.MatchingModule
import me.santiagoreina.parcheu.api.matching.MatchingRoutes
import me.santiagoreina.parcheu.api.matching.inMemoryMatchingModule
import me.santiagoreina.parcheu.api.matching.matchingModule
import me.santiagoreina.parcheu.api.notifications.center.NotificationCenterModule
import me.santiagoreina.parcheu.api.notifications.center.NotificationsRoutes
import me.santiagoreina.parcheu.api.notifications.center.exposedNotificationCenter
import me.santiagoreina.parcheu.api.notifications.center.inMemoryNotificationCenter
import me.santiagoreina.parcheu.api.notifications.push.ExposedPushStore
import me.santiagoreina.parcheu.api.notifications.push.FakeFcmClient
import me.santiagoreina.parcheu.api.notifications.push.FcmNotifier
import me.santiagoreina.parcheu.api.notifications.push.FirebaseFcmClient
import me.santiagoreina.parcheu.api.platform.notifier.NoopNotifier
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
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
import me.santiagoreina.parcheu.api.safety.SafetyModule
import me.santiagoreina.parcheu.api.safety.SafetyRoutes
import me.santiagoreina.parcheu.api.safety.inMemorySafetyModule
import me.santiagoreina.parcheu.api.safety.safetyModule
import me.santiagoreina.parcheu.api.scenario.ScenarioRoutes
import me.santiagoreina.parcheu.api.simulation.NotifierSimulationNotifier
import me.santiagoreina.parcheu.api.simulation.SimulationModule
import me.santiagoreina.parcheu.api.simulation.SimulationRoutes
import me.santiagoreina.parcheu.api.simulation.inMemorySimulationModule
import me.santiagoreina.parcheu.api.simulation.simulationModule
import me.santiagoreina.parcheu.api.social.SocialRoutes
import me.santiagoreina.parcheu.api.social.invites.InviteLinks
import me.santiagoreina.parcheu.api.social.invites.InviteModule
import me.santiagoreina.parcheu.api.social.invites.exposedInviteModule
import me.santiagoreina.parcheu.api.social.invites.inMemoryInviteModule
import me.santiagoreina.parcheu.api.platform.tasks.CloudTasksQueue
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.twin.TwinModule
import me.santiagoreina.parcheu.api.twin.TwinRoutes
import me.santiagoreina.parcheu.api.twin.inMemoryTwinModule
import me.santiagoreina.parcheu.api.twin.twinModule
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
    /** Perfil básico B0 y verificación de edad (E2.6). */
    val profile: ProfileService,
    /** Cuotas (E4.1). Por defecto en memoria: solo [fromConfig] cablea Postgres. */
    val quota: QuotaModule = inMemoryQuotaModule(clock),
    /** Pasarela de IA (E0.9). Por defecto Fake con uso en memoria (tests y `ENV=local`). */
    val ai: AiGateway = localAiGateway(clock),
    /** Gemelo (E2.3). Por defecto en memoria: solo [fromConfig] cablea Postgres y Cloud Tasks. */
    val twin: TwinModule = inMemoryTwinModule(clock, ai),
    /** Grafo (E3.1). Por defecto en memoria: solo [fromConfig] cablea Postgres y la clave de `anonId`. */
    val graph: GraphModule = inMemoryGraphModule(clock),
    /** Avisos (push + bandeja, E5.3). Los tests de otros dominios pasan un `FakeNotifier`. */
    val notifier: Notifier = NoopNotifier,
    /** Chat (E5.1). Por defecto en memoria (repositorio y espejo): solo [fromConfig] cablea Postgres y Firestore. */
    val chat: ChatModule = inMemoryChatModule(clock, graph.policy, notifier),
    /**
     * Simulación (E4.3) e informe y decisión mutua (E4.6, con [chat] y [notifier]). Por defecto en
     * memoria: solo [fromConfig] cablea Postgres, Cloud Tasks y Firestore.
     */
    val simulation: SimulationModule = inMemorySimulationModule(
        clock, ai, quota, graph, notifier = NotifierSimulationNotifier(notifier), chat = chat, push = notifier,
    ),
    /** Fila de match (E4.2). Por defecto en memoria: solo [fromConfig] cablea Postgres y Firestore. */
    val matching: MatchingModule = inMemoryMatchingModule(clock, quota, simulation, graph, notifier),
    /**
     * Reportar y bloquear (E8.1). Por defecto en memoria (propaga los bloqueos a chat, grafo y fila):
     * solo [fromConfig] cablea Postgres.
     */
    val safety: SafetyModule = inMemorySafetyModule(clock, chat, graph, matching),
    /**
     * Borrado y exportación de la cuenta (E8.3). Por defecto en memoria: solo [fromConfig] cablea
     * Postgres, la cola `maintenance`, Firestore y Firebase Auth.
     */
    val account: AccountModule = inMemoryAccountModule(identity, graph, twin, clock),
    /** Centro de notificaciones C2 (E5.4). Por defecto en memoria: solo [fromConfig] cablea Postgres. */
    val notifications: NotificationCenterModule = inMemoryNotificationCenter(clock, quota, graph),
    /**
     * No leídas de la campana de Inicio (E4.7). Cuenta en la bandeja de [notifications] (E5.4, en Postgres
     * con [fromConfig]): 'Marcar leídas' deja la campana en 0 y los avisos de bloqueados no cuentan.
     */
    val unread: UnreadCounter = UnreadCounter { notifications.service.unread(it) },
    /** Invitaciones por QR y código (E6.1). Por defecto en memoria: solo [fromConfig] cablea Postgres. */
    val invites: InviteModule = inMemoryInviteModule(clock, graph, simulation, notifier),
    /**
     * Billing (E7.1): verify, derecho vigente y RTDN. Por defecto en memoria y sin API de Play: solo
     * [fromConfig] cablea Postgres (la misma tabla `entitlements` que lee [quota]) y Google Play.
     */
    val billing: BillingModule = inMemoryBillingModule(clock),
    /** Dominios con rutas. Los tests pueden agregar rutas propias; el orden no importa. */
    val domains: List<DomainRoutes> =
        allDomains(
            identity, profile, quota, twin, graph, chat, simulation, matching, safety, account, unread, notifications,
            invites, billing, config.env,
        ),
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
            val profile = ProfileService(identity, ExposedProfileRepository(database), clock)
            val quota = quotaModule(config.env, database, clock)
            val ai = aiGateway(config, vars, clock) { database }
            val tasks = if (local) FakeTaskQueue() else CloudTasksQueue.fromEnv(config, vars)
            val twin = twinModule(config.env, database, clock, ai, tasks)
            val graph = GraphModule(
                ExposedGraphRepository(database), AnonIdSigner.fromEnv(config.env), clock, ExposedFofRepository(database),
            )
            // En local no hay credenciales de FCM: se guarda el aviso y el envío es un Fake (E5.3).
            val fcm = if (local) FakeFcmClient() else FirebaseFcmClient(requireNotNull(config.firebaseProjectId))
            val notifier = FcmNotifier(ExposedPushStore(database), fcm, clock)
            // En local no hay Firestore: el espejo del chat queda en memoria (Postgres sigue siendo la verdad).
            val chatMirror =
                if (local) InMemoryChatMirror() else FirestoreChatMirror.forProject(requireNotNull(config.firebaseProjectId))
            val chat = ChatModule(ExposedChatRepository(database), chatMirror, notifier, graph.policy, clock)
            // Simulación (E4.3): misma base, cola y avisos; el espejo de progreso va a Firestore fuera de local.
            // El informe y la decisión mutua (E4.6) reutilizan el chat (DIRECT) y el notifier (NEW_CONNECTION).
            // Los demo deciden con `DEMO_CONNECT_PROBABILITY` (E4.9, ADR-0027).
            val demo = DemoSettings.fromEnv(config.env, vars)
            val simulation = simulationModule(
                config.env, database, clock, ai, quota, graph, tasks, config.firebaseProjectId,
                notifier = NotifierSimulationNotifier(notifier), chat = chat, push = notifier,
                demo = DemoConnectPolicy(demo.connectProbability), demoUsersEnabled = demo.enabled,
            )
            // Fila de match (E4.2): misma base, cuotas, simulación, grafo y avisos; espejo matchTickets en Firestore fuera de local.
            val matching = matchingModule(
                config.env, database, clock, quota, simulation, graph, notifier, config.firebaseProjectId,
                MatchingFlags.fromEnv(config.env, vars),
            )
            // Reportar y bloquear (E8.1): la misma tabla `blocks` la leen chat, grafo, fila y simulación.
            val safety = safetyModule(database, chat, graph, matching, clock)
            // Borrado y exportación (E8.3): purga por la cola `maintenance`; Firestore y Auth reales fuera de local.
            val account = accountModule(
                config.env, database, identity, graph, twin, tasks, config.firebaseProjectId, clock,
                closedMatches = matching.service::onMatchesClosedByDeletion,
            )
            // Invitaciones (E6.1): aristas por EdgeService, modos de user_modes y enlace https://<hosting>/i/{codigo}.
            val invites = exposedInviteModule(database, graph, simulation, notifier, InviteLinks.fromConfig(config, vars), clock)
            // Billing (E7.1, ADR-0013): API de Play suplantando a `sa-play` (`PLAY_SERVICE_ACCOUNT`) o con la llave de `SECRET_PLAY_SA`.
            val billing = billingModule(config.env, database, clock, vars)
            return AppContainer(
                config, clock, tokenVerifier, oidcVerifier, identity, profile, quota,
                ai = ai, twin = twin, graph = graph, notifier = notifier, chat = chat, simulation = simulation,
                matching = matching, safety = safety, account = account,
                notifications = exposedNotificationCenter(database, clock, quota, graph),
                billing = billing,
                invites = invites,
            )
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

        internal fun localAiGateway(clock: Clock): AiGateway {
            val config = AiConfig.fromEnv(emptyMap(), Env.LOCAL)
            return AiModule.gateway(config, InMemoryAiUsageStore(), clock, FakeLlmClient())
        }
    }
}

/** Todos los dominios de arquitectura.md §3.1 que exponen rutas. */
fun allDomains(
    identity: IdentityService,
    profile: ProfileService,
    quota: QuotaModule,
    twin: TwinModule,
    graph: GraphModule = inMemoryGraphModule(systemClock()),
    chat: ChatModule = inMemoryChatModule(systemClock(), graph.policy),
    simulation: SimulationModule = inMemorySimulationModule(systemClock(), AppContainer.localAiGateway(systemClock()), quota, graph),
    matching: MatchingModule = inMemoryMatchingModule(systemClock(), quota, simulation, graph, NoopNotifier),
    safety: SafetyModule = inMemorySafetyModule(systemClock(), chat, graph, matching),
    account: AccountModule = inMemoryAccountModule(identity, graph, twin, systemClock()),
    unread: UnreadCounter = UnreadCounter.NONE,
    notifications: NotificationCenterModule = inMemoryNotificationCenter(systemClock(), quota, graph),
    invites: InviteModule = inMemoryInviteModule(systemClock(), graph, simulation, NoopNotifier),
    billing: BillingModule = inMemoryBillingModule(systemClock()),
    /** Con `PROD` no se registra `POST /internal/dev/grant-plus` (404), aunque [quota] traiga concesión. */
    env: Env = Env.LOCAL,
): List<DomainRoutes> = listOf(
    IdentityRoutes(identity, profile, account),
    TwinRoutes(twin),
    QuotaRoutes(quota),
    MatchingRoutes(matching) { firebase -> identity.requireUser(firebase).id },
    SimulationRoutes(simulation) { firebase -> identity.requireUser(firebase).id },
    ScenarioRoutes(),
    GraphRoutes(graph.service, fofService(graph, twin, quota, matching.flags.demoUsersEnabled)) { firebase ->
        identity.requireUser(firebase).id
    },
    SocialRoutes(invites.service) { firebase -> identity.requireUser(firebase).id },
    ChatRoutes(chat.service) { firebase -> identity.requireUser(firebase) },
    // Centro de notificaciones C2 (E5.4): bandeja agrupada por fecha de Bogotá y 'Marcar leídas'.
    NotificationsRoutes(notifications.service) { firebase -> identity.requireUser(firebase).id },
    // Billing (E7.1): verify, derecho vigente, RTDN y, fuera de prod, `grant-plus`.
    BillingRoutes(billing.service, quota.devPlus.takeIf { env != Env.PROD }, quota.clock) { firebase ->
        identity.requireUser(firebase).id
    },
    SafetyRoutes(safety.service) { firebase -> identity.requireUser(firebase).id },
    AdminRoutes(),
    // Inicio (E4.7): agregador de cupos, simulaciones, sugerencias del grafo y no leídas.
    // Las sugerencias solo ofrecen modos activos para los dos, como exige SimulationService.create.
    HomeRoutes(HomeService(quota.service, simulation.service, homeSuggestions(graph, simulation, matching.flags.demoUsersEnabled), unread)) { firebase ->
        identity.requireUser(firebase).id
    },
)

/**
 * Amigos de amigos (E6.3): la consulta de Postgres si el módulo del grafo la trae; si no, la versión en
 * memoria con los rasgos del gemelo en memoria. ParcheU+ decide si viajan los `items`.
 */
private fun fofService(graph: GraphModule, twin: TwinModule, quota: QuotaModule, demoUsersEnabled: Boolean): FofService {
    val repository = graph.fofRepository ?: InMemoryFofRepository(graph.repository) { twin.repository.state(it)?.traits }
    return FofService(repository, graph.repository, graph.policy, { quota.service.isPlus(it) }, demoUsersEnabled)
}

/**
 * Sugerencias de Inicio con los modos activos leídos de `user_modes` (vía el repositorio de simulaciones).
 * [demoUsersEnabled] es `DEMO_USERS_ENABLED`, la misma bandera que aplica la fila (E4.9, ADR-0027).
 */
private fun homeSuggestions(graph: GraphModule, simulation: SimulationModule, demoUsersEnabled: Boolean) =
    HomeSuggestions(graph.repository, graph.policy, demoUsersEnabled) { userId ->
        simulation.repository.participant(userId)?.takeIf { !it.deleted }?.activeModes.orEmpty()
    }
