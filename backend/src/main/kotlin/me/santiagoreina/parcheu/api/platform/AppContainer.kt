package me.santiagoreina.parcheu.api.platform

import me.santiagoreina.parcheu.api.admin.AdminModule
import me.santiagoreina.parcheu.api.admin.AdminRoutes
import me.santiagoreina.parcheu.api.admin.adminModule
import me.santiagoreina.parcheu.api.admin.inMemoryAdminModule
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
import me.santiagoreina.parcheu.api.graph.communities.CommunityModule
import me.santiagoreina.parcheu.api.graph.communities.CommunityService
import me.santiagoreina.parcheu.api.graph.communities.CommunityRoutes
import me.santiagoreina.parcheu.api.graph.communities.ExposedCommunityRepository
import me.santiagoreina.parcheu.api.graph.communities.communityModule
import me.santiagoreina.parcheu.api.graph.communities.inMemoryCommunityModule
import me.santiagoreina.parcheu.api.graph.fof.ExposedFofRepository
import me.santiagoreina.parcheu.api.graph.fof.FofService
import me.santiagoreina.parcheu.api.graph.fof.InMemoryFofRepository
import me.santiagoreina.parcheu.api.graph.history.HistoryService
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
import me.santiagoreina.parcheu.api.safety.AnonymousContacts
import me.santiagoreina.parcheu.api.safety.SafetyModule
import me.santiagoreina.parcheu.api.safety.SafetyRoutes
import me.santiagoreina.parcheu.api.safety.inMemorySafetyModule
import me.santiagoreina.parcheu.api.safety.moderation.ModerationService
import me.santiagoreina.parcheu.api.safety.moderation.attachTo
import me.santiagoreina.parcheu.api.safety.moderation.moderationEnabled
import me.santiagoreina.parcheu.api.safety.moderation.moderationService
import me.santiagoreina.parcheu.api.safety.safetyModule
import me.santiagoreina.parcheu.api.scenario.ScenarioModule
import me.santiagoreina.parcheu.api.social.invites.ExposedInviteRepository
import me.santiagoreina.parcheu.api.scenario.ScenarioRoutes
import me.santiagoreina.parcheu.api.scenario.ScenarioService
import me.santiagoreina.parcheu.api.scenario.inMemoryScenarioModule
import me.santiagoreina.parcheu.api.scenario.scenarioModule
import me.santiagoreina.parcheu.api.simulation.NotifierSimulationNotifier
import me.santiagoreina.parcheu.api.simulation.SimulationModule
import me.santiagoreina.parcheu.api.simulation.SimulationRoutes
import me.santiagoreina.parcheu.api.simulation.inMemorySimulationModule
import me.santiagoreina.parcheu.api.simulation.simulationModule
import me.santiagoreina.parcheu.api.social.SocialRoutes
import me.santiagoreina.parcheu.api.social.introductions.ExposedIntroductionRepository
import me.santiagoreina.parcheu.api.social.introductions.InMemoryIntroductionRepository
import me.santiagoreina.parcheu.api.social.introductions.IntroductionModule
import me.santiagoreina.parcheu.api.social.introductions.IntroductionRepository
import me.santiagoreina.parcheu.api.social.introductions.IntroductionRoutes
import me.santiagoreina.parcheu.api.social.introductions.IntroductionSimulationNotifier
import me.santiagoreina.parcheu.api.social.introductions.introductionModule
import me.santiagoreina.parcheu.api.social.requests.ConnectRequestModule
import me.santiagoreina.parcheu.api.social.requests.ConnectRequestRoutes
import me.santiagoreina.parcheu.api.social.requests.exposedConnectRequestModule
import me.santiagoreina.parcheu.api.social.requests.inMemoryConnectRequestModule
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
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import me.santiagoreina.parcheu.api.platform.jobs.InProcessScheduler
import me.santiagoreina.parcheu.api.platform.tasks.InProcessTaskQueue
import me.santiagoreina.parcheu.api.platform.tasks.TaskQueue
import me.santiagoreina.parcheu.api.platform.tasks.TasksMode
import me.santiagoreina.parcheu.api.simulation.SimulationService
import me.santiagoreina.parcheu.api.twin.TaskPayload
import me.santiagoreina.parcheu.api.twin.TwinService
import me.santiagoreina.parcheu.api.twin.memories.MemoryService
import me.santiagoreina.parcheu.api.identity.deletion.AccountDeletionService
import me.santiagoreina.parcheu.contract.ContractJson

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
    val graph: GraphModule = inMemoryGraphModule(clock, isPlus = { quota.service.isPlus(it) }),
    /** Avisos (push + bandeja, E5.3). Los tests de otros dominios pasan un `FakeNotifier`. */
    val notifier: Notifier = NoopNotifier,
    /** Chat (E5.1). Por defecto en memoria (repositorio y espejo): solo [fromConfig] cablea Postgres y Firestore. */
    val chat: ChatModule = inMemoryChatModule(clock, graph.policy, notifier),
    /**
     * Presentaciones guardadas (E6.2). Va antes de [simulation] porque el aviso de fin de simulación lo
     * consulta para no avisar a las personas presentadas antes de la propuesta.
     */
    val introductionStore: IntroductionRepository = InMemoryIntroductionRepository(),
    /**
     * Simulación (E4.3) e informe y decisión mutua (E4.6, con [chat] y [notifier]). Por defecto en
     * memoria: solo [fromConfig] cablea Postgres, Cloud Tasks y Firestore.
     */
    val simulation: SimulationModule = inMemorySimulationModule(
        clock, ai, quota, graph,
        notifier = IntroductionSimulationNotifier(introductionStore, NotifierSimulationNotifier(notifier), notifier),
        chat = chat, push = notifier,
    ),
    /** Fila de match (E4.2). Por defecto en memoria: solo [fromConfig] cablea Postgres y Firestore. */
    val matching: MatchingModule = inMemoryMatchingModule(clock, quota, simulation, graph, notifier),
    /**
     * Reportar y bloquear (E8.1). Por defecto en memoria (propaga los bloqueos a chat, grafo y fila):
     * solo [fromConfig] cablea Postgres.
     */
    val safety: SafetyModule = inMemorySafetyModule(clock, chat, graph, matching, simulation = simulation),
    /**
     * Borrado y exportación de la cuenta (E8.3). Por defecto en memoria: solo [fromConfig] cablea
     * Postgres, la cola `maintenance`, Firestore y Firebase Auth.
     */
    val account: AccountModule = inMemoryAccountModule(identity, graph, twin, clock),
    /** Presentar a dos amigos (E6.2): sobre [introductionStore], [simulation], [chat] y [notifier]. */
    val introductions: IntroductionModule =
        introductionModule(introductionStore, simulation, graph, chat, quota, notifier, clock),
    /** Centro de notificaciones C2 (E5.4). Por defecto en memoria: solo [fromConfig] cablea Postgres. */
    val notifications: NotificationCenterModule = inMemoryNotificationCenter(clock, quota, graph),
    /**
     * No leídas de la campana de Inicio (E4.7). Cuenta en la bandeja de [notifications] (E5.4, en Postgres
     * con [fromConfig]): 'Marcar leídas' deja la campana en 0 y los avisos de bloqueados no cuentan.
     */
    val unread: UnreadCounter = UnreadCounter { notifications.service.unread(it) },
    /** Invitaciones por QR y código (E6.1). Por defecto en memoria: solo [fromConfig] cablea Postgres. */
    val invites: InviteModule = inMemoryInviteModule(clock, graph, simulation, notifier),
    /** 'Quién quiere conectar contigo' (E6.6). Por defecto lee las decisiones en memoria de [simulation]. */
    val connectRequests: ConnectRequestModule = inMemoryConnectRequestModule(simulation, graph, quota),
    /**
     * Billing (E7.1): verify, derecho vigente y RTDN. Por defecto en memoria y sin API de Play: solo
     * [fromConfig] cablea Postgres (la misma tabla `entitlements` que lee [quota]) y Google Play.
     */
    val billing: BillingModule = inMemoryBillingModule(clock),
    /** Administración de reportes (E8.5). Por defecto en memoria: solo [fromConfig] cablea Postgres. */
    val admin: AdminModule = inMemoryAdminModule(identity.repo, chat, safety, clock),
    /** Modo escenario (E4.8): acuerdo, cierre y juez sobre [chat] y [simulation]. Por defecto en memoria. */
    val scenario: ScenarioModule = inMemoryScenarioModule(clock, ai, quota, graph, chat, simulation, notifier),
    /**
     * Comunidades (E6.4): job semanal, nombre con el modelo lite y `GET /v1/communities`. Por defecto en
     * memoria sobre [graph]: solo [fromConfig] cablea Postgres y la cola de tareas.
     */
    val communities: CommunityModule = inMemoryCommunityModule(graph, ai, quota, clock, traitsOf = { twin.repository.state(it)?.traits }),
    /**
     * Moderación con IA (E8.6, ADR-0026). Por defecto con Fake y sin engancharse al chat: solo [fromConfig]
     * la conecta con la cola `ai-light` (salvo `MODERATION_ENABLED=false`).
     */
    val moderation: ModerationService = moderationService(ai, chat, safety, FakeTaskQueue(), clock, notifier),
    /**
     * Cola en proceso y programador de jobs (`TASKS_MODE=inline`, ADR-0033); null con Cloud Tasks o Fake.
     * Main lo arranca (`start`) en modo `serve` y lo cierra al parar.
     */
    val background: BackgroundWork? = null,
    /** Dominios con rutas. Los tests pueden agregar rutas propias; el orden no importa. */
    val domains: List<DomainRoutes> =
        allDomains(
            identity, profile, quota, twin, graph, chat, simulation, matching, safety, account, unread, notifications,
            invites, billing, config.env, introductions, admin, communities, scenario, connectRequests, moderation,
        ),
) {
    companion object {
        /**
         * Con `ENV=local` y `LOCAL_FAKE_ADMIN=true`, el token `fake-admin` lleva el claim `admin` (E8.5).
         * Es opt-in porque el homeserver arranca en `local` con datos reales de testers: sin la variable
         * nadie puede moderar ni leer reportes con un token inventado. Nunca existe en dev ni prod.
         */
        const val LOCAL_ADMIN_UID = "admin"

        /** Hora de Bogotá del job `memories` en el homeserver (la misma que `scheduler.tf`: `0 2 * * *`). */
        val MEMORIES_AT: LocalTime = LocalTime.of(2, 0)

        /** El job `retention` corre a diario a las 03:30 de Bogotá, como `scheduler.tf` (E8.8). */
        val RETENTION_AT: LocalTime = LocalTime.of(3, 30)

        /** El job `twin-accuracy` corre los domingos a las 23:00 de Bogotá, como `scheduler.tf` (E9.3). */
        val TWIN_ACCURACY_DAY: DayOfWeek = DayOfWeek.SUNDAY
        val TWIN_ACCURACY_AT: LocalTime = LocalTime.of(23, 0)

        /**
         * Cableado de producción: verificadores reales (Fake solo con `ENV=local`). La
         * configuración de IA se valida aquí, así que un `AI_PROVIDER` inválido hace fallar el
         * arranque (E0.9).
         */
        fun fromConfig(config: AppConfig, vars: Map<String, String> = System.getenv()): AppContainer {
            val local = config.env == Env.LOCAL
            val tokenVerifier = if (local) {
                FakeTokenVerifier(adminUids = if (vars["LOCAL_FAKE_ADMIN"] == "true") setOf(LOCAL_ADMIN_UID) else emptySet())
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
            val mode = TasksMode.fromEnv(config.env, vars)
            val inlineQueue = if (mode == TasksMode.INLINE) InProcessTaskQueue(clock) else null
            val tasks: TaskQueue = when (mode) {
                TasksMode.FAKE -> FakeTaskQueue()
                TasksMode.INLINE -> checkNotNull(inlineQueue)
                TasksMode.CLOUD -> CloudTasksQueue.fromEnv(config, vars)
            }
            val twin = twinModule(config.env, database, clock, ai, tasks)
            val graph = GraphModule(
                ExposedGraphRepository(database), AnonIdSigner.fromEnv(config.env), clock, ExposedFofRepository(database),
                isPlus = { quota.service.isPlus(it) },
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
            // Presentaciones (E6.2): el fin de una simulación de presentación solo avisa a quien presenta.
            val introductionStore = ExposedIntroductionRepository(database)
            val simulation = simulationModule(
                config.env, database, clock, ai, quota, graph, tasks, config.firebaseProjectId,
                notifier = IntroductionSimulationNotifier(introductionStore, NotifierSimulationNotifier(notifier), notifier),
                chat = chat, push = notifier,
                demo = DemoConnectPolicy(demo.connectProbability), demoUsersEnabled = demo.enabled,
            )
            // Fila de match (E4.2): misma base, cuotas, simulación, grafo y avisos; espejo matchTickets en Firestore fuera de local.
            // Al crear el chat SCENARIO se escribe `chats/{id}` en Firestore (E4.8; pendientes-humanos E4.2 paso 4).
            val matching = matchingModule(
                config.env, database, clock, quota, simulation, graph, notifier, config.firebaseProjectId,
                MatchingFlags.fromEnv(config.env, vars),
                onScenarioChatCreated = { chatId -> chat.service.mirrorChat(chatId) },
            )
            // Modo escenario (E4.8): acuerdo, cierre y juez; `judge-scenario` va por la cola `simulations`.
            val scenario = scenarioModule(clock, ai, quota, graph, chat, simulation, tasks, notifier, moderationEnabled(vars))
            // Reportar y bloquear (E8.1): la misma tabla `blocks` la leen chat, grafo, fila y simulación.
            // E8.7: el `anonId` de un reporte o bloqueo también se resuelve entre los canjes de C5, los amigos de
            // amigos de D6 y las presentaciones de E3 del observador.
            val safetyContacts = listOf(
                AnonymousContacts.pendingInvites(ExposedInviteRepository(database)),
                AnonymousContacts.friendsOfFriends(
                    FofService(graph.fofRepository ?: InMemoryFofRepository(graph.repository), graph.repository, graph.policy, { true }, demo.enabled),
                ),
                AnonymousContacts.introductions(introductionStore),
            )
            val safety = safetyModule(database, chat, graph, matching, clock, safetyContacts)
            // Moderación con IA (E8.6): tarea `moderate` en `ai-light` después de entregar cada mensaje (fail-open).
            val moderation = moderationService(ai, chat, safety, tasks, clock, notifier)
            if (moderationEnabled(vars)) moderation.attachTo(chat)
            // Administración de reportes (E8.5): sobre la misma tabla `reports`; suspende por `users.suspended_until`.
            val admin = adminModule(database, identity.repo, chat, clock)
            // Borrado y exportación (E8.3): purga por la cola `maintenance`; Firestore y Auth reales fuera de local.
            val account = accountModule(
                config.env, database, identity, graph, twin, tasks, config.firebaseProjectId, clock,
                closedMatches = matching.service::onMatchesClosedByDeletion,
            )
            val introductions = introductionModule(
                introductionStore, simulation, graph, chat, quota, notifier, clock,
                demo = DemoConnectPolicy(demo.connectProbability), demoUsersEnabled = demo.enabled,
            )
            // Invitaciones (E6.1): aristas por EdgeService, modos de user_modes y enlace https://<hosting>/i/{codigo}.
            val invites = exposedInviteModule(database, graph, simulation, notifier, InviteLinks.fromConfig(config, vars), clock)
            // Solicitudes de conexión (E6.6): lee `connection_decisions` de la misma base.
            val connectRequests = exposedConnectRequestModule(database, graph, quota, demo.enabled)
            // Billing (E7.1, ADR-0013): API de Play suplantando a `sa-play` (`PLAY_SERVICE_ACCOUNT`) o con la llave de `SECRET_PLAY_SA`.
            val billing = billingModule(config.env, database, clock, vars)
            // Comunidades (E6.4): la tarea name-community y el job semanal también corren en el homeserver.
            val communities = communityModule(
                ExposedCommunityRepository(database), graph, ai, quota, tasks, clock, matching.flags.demoUsersEnabled,
            )
            val background = inlineQueue?.let { queue ->
                inlineBackground(queue, clock, twin, simulation, matching, account, scenario, communities, moderation)
            }
            return AppContainer(
                config, clock, tokenVerifier, oidcVerifier, identity, profile, quota,
                ai = ai, twin = twin, graph = graph, notifier = notifier, chat = chat,
                introductionStore = introductionStore, simulation = simulation,
                matching = matching, safety = safety, moderation = moderation, admin = admin, account = account, introductions = introductions,
                notifications = exposedNotificationCenter(database, clock, quota, graph),
                billing = billing,
                invites = invites,
                communities = communities,
                connectRequests = connectRequests,
                // Sin esto el valor por defecto (en memoria, FakeTaskQueue inline) atendería las rutas y pisaría el gancho del chat.
                scenario = scenario,
                background = background,
            )
        }

        /**
         * Handlers de las ocho tareas reales (los mismos que `POST /internal/tasks/...`) y jobs del programador.
         * El job `memories` corre a diario a las 02:00 de Bogotá, como `scheduler.tf`; el nombre de la tarea
         * (`extract-memories-<uuid>-<yyyyMMdd>`) deduplica si el job se repite el mismo día.
         * El job `retention` (E8.8) corre a diario a las 03:30 de Bogotá. `twin-accuracy` (E9.3) corre los domingos a las 23:00 de Bogotá.
         * `judge-scenario` y `scenario-sweep` (E4.8) corren con el módulo real de escenario.
         * `name-community` y el job `communities` (E6.4) corren con el módulo real de comunidades.
         */
        private fun inlineBackground(
            queue: InProcessTaskQueue,
            clock: Clock,
            twin: TwinModule,
            simulation: SimulationModule,
            matching: MatchingModule,
            account: AccountModule,
            scenario: ScenarioModule,
            communities: CommunityModule,
            moderation: ModerationService,
        ): BackgroundWork {
            fun id(body: String) = ContractJson.decodeFromString<TaskPayload>(body).uuid()
            queue.register(TwinService.BUILD_PERSONA_PATH) { twin.service.buildPersona(id(it)) }
            queue.register(SimulationService.SIMULATE_PATH) { simulation.engine.run(id(it)) }
            queue.register(AccountDeletionService.PURGE_PATH) { account.deletion.purge(id(it)) }
            queue.register(CommunityService.NAME_PATH) { communities.service.nameCommunity(id(it)) }
            queue.register(ScenarioService.JUDGE_PATH) { scenario.service.judge(id(it)) }
            queue.register(MemoryService.TASK_PATH) { twin.memories.extract(id(it)) }
            queue.register(ModerationService.TASK_PATH) { moderation.moderate(id(it)) }
            queue.register(ModerationService.HELP_TASK_PATH) { moderation.deliverHelp(id(it)) }
            return BackgroundWork(queue, InProcessScheduler()) { scheduler ->
                scheduler.every("match-sweep", Duration.ofSeconds(60)) { matching.service.sweep() }
                scheduler.every("scenario-sweep", Duration.ofSeconds(60), Duration.ofSeconds(20)) { scenario.service.sweep() }
                scheduler.every("purge-sweep", Duration.ofHours(1), Duration.ofMinutes(2)) { account.deletion.sweepStalled() }
                // Comunidades (E6.4): revisa cada hora y corre una vez por semana desde el lunes 03:00 de Bogotá.
                scheduler.every("communities", Duration.ofHours(1), Duration.ofMinutes(3)) { communities.service.runIfDue() }
                scheduler.every("simulation-recovery", Duration.ofMinutes(15), Duration.ofSeconds(30)) {
                    simulation.service.requeueUnfinished()
                }
                scheduler.every("memories", Duration.ofDays(1), InProcessScheduler.delayUntil(MEMORIES_AT, clock)) {
                    twin.memories.enqueueNightly()
                }
                scheduler.every("retention", Duration.ofDays(1), InProcessScheduler.delayUntil(RETENTION_AT, clock)) {
                    account.retention.run()
                }
                scheduler.every(
                    "twin-accuracy",
                    Duration.ofDays(7),
                    InProcessScheduler.delayUntilWeekly(TWIN_ACCURACY_DAY, TWIN_ACCURACY_AT, clock),
                ) {
                    twin.feedback.computeAccuracy()
                }
            }
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
    graph: GraphModule = inMemoryGraphModule(systemClock(), isPlus = { quota.service.isPlus(it) }),
    chat: ChatModule = inMemoryChatModule(systemClock(), graph.policy),
    simulation: SimulationModule = inMemorySimulationModule(systemClock(), AppContainer.localAiGateway(systemClock()), quota, graph),
    matching: MatchingModule = inMemoryMatchingModule(systemClock(), quota, simulation, graph, NoopNotifier),
    safety: SafetyModule = inMemorySafetyModule(systemClock(), chat, graph, matching, simulation = simulation),
    account: AccountModule = inMemoryAccountModule(identity, graph, twin, systemClock()),
    unread: UnreadCounter = UnreadCounter.NONE,
    notifications: NotificationCenterModule = inMemoryNotificationCenter(systemClock(), quota, graph),
    invites: InviteModule = inMemoryInviteModule(systemClock(), graph, simulation, NoopNotifier),
    billing: BillingModule = inMemoryBillingModule(systemClock()),
    /** Con `PROD` no se registra `POST /internal/dev/grant-plus` (404), aunque [quota] traiga concesión. */
    env: Env = Env.LOCAL,
    introductions: IntroductionModule =
        introductionModule(InMemoryIntroductionRepository(), simulation, graph, chat, quota, NoopNotifier, systemClock()),
    admin: AdminModule = inMemoryAdminModule(identity.repo, chat, safety, systemClock()),
    communities: CommunityModule = inMemoryCommunityModule(
        graph, AppContainer.localAiGateway(systemClock()), quota, systemClock(), traitsOf = { twin.repository.state(it)?.traits },
    ),
    scenario: ScenarioModule = inMemoryScenarioModule(systemClock(), AppContainer.localAiGateway(systemClock()), quota, graph, chat, simulation, NoopNotifier),
    connectRequests: ConnectRequestModule = inMemoryConnectRequestModule(simulation, graph, quota),
    moderation: ModerationService =
        moderationService(AppContainer.localAiGateway(systemClock()), chat, safety, FakeTaskQueue(), systemClock()),
): List<DomainRoutes> = listOf(
    IdentityRoutes(identity, profile, account),
    TwinRoutes(twin),
    QuotaRoutes(quota),
    MatchingRoutes(matching) { firebase -> identity.requireUser(firebase).id },
    SimulationRoutes(simulation) { firebase -> identity.requireUser(firebase).id },
    ScenarioRoutes(scenario.service) { firebase -> identity.requireUser(firebase).id },
    GraphRoutes(
        graph.service,
        fofService(graph, twin, quota, matching.flags.demoUsersEnabled),
        // Evolución de la red (E6.5): derivada de las aristas en cada consulta, sin tarea ni job.
        HistoryService(graph.repository, graph.policy, { quota.service.isPlus(it) }, quota.clock),
    ) { firebase ->
        identity.requireUser(firebase).id
    },
    // Comunidades semanales (E6.4).
    CommunityRoutes(communities) { firebase -> identity.requireUser(firebase).id },
    SocialRoutes(invites.service) { firebase -> identity.requireUser(firebase).id },
    // Quién quiere conectar contigo (E6.6).
    ConnectRequestRoutes(connectRequests.service) { firebase -> identity.requireUser(firebase).id },
    // Presentar a dos amigos (E6.2).
    IntroductionRoutes(introductions.service) { firebase -> identity.requireUser(firebase).id },
    ChatRoutes(chat.service) { firebase -> identity.requireUser(firebase) },
    // Centro de notificaciones C2 (E5.4): bandeja agrupada por fecha de Bogotá y 'Marcar leídas'.
    NotificationsRoutes(notifications.service) { firebase -> identity.requireUser(firebase).id },
    // Billing (E7.1): verify, derecho vigente, RTDN y, fuera de prod, `grant-plus`.
    BillingRoutes(billing.service, quota.devPlus.takeIf { env != Env.PROD }, quota.clock) { firebase ->
        identity.requireUser(firebase).id
    },
    SafetyRoutes(safety.service, moderation) { firebase -> identity.requireUser(firebase).id },
    // Administración de reportes (E8.5): claim `admin`; `resolved_by` es la cuenta del admin si la tiene.
    AdminRoutes(admin.service) { firebase -> identity.repo.findByFirebaseUid(firebase.uid)?.id },
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
