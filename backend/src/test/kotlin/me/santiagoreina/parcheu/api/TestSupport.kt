package me.santiagoreina.parcheu.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import me.santiagoreina.parcheu.api.billing.BillingModule
import me.santiagoreina.parcheu.api.billing.inMemoryBillingModule
import me.santiagoreina.parcheu.api.identity.IdentityService
import me.santiagoreina.parcheu.api.identity.deletion.AccountModule
import me.santiagoreina.parcheu.api.identity.deletion.inMemoryAccountModule
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.identity.profile.InMemoryProfileRepository
import me.santiagoreina.parcheu.api.identity.profile.ProfileService
import me.santiagoreina.parcheu.api.quota.QuotaModule
import me.santiagoreina.parcheu.api.quota.inMemoryQuotaModule
import me.santiagoreina.parcheu.api.ai.AiGateway
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.admin.AdminModule
import me.santiagoreina.parcheu.api.admin.inMemoryAdminModule
import me.santiagoreina.parcheu.api.safety.SafetyModule
import me.santiagoreina.parcheu.api.safety.moderation.ModerationService
import me.santiagoreina.parcheu.api.safety.moderation.moderationService
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.social.invites.InviteModule
import me.santiagoreina.parcheu.api.social.requests.ConnectRequestModule
import me.santiagoreina.parcheu.api.social.requests.inMemoryConnectRequestModule
import me.santiagoreina.parcheu.api.social.invites.inMemoryInviteModule
import me.santiagoreina.parcheu.api.safety.inMemorySafetyModule
import me.santiagoreina.parcheu.api.graph.GraphModule
import me.santiagoreina.parcheu.api.graph.communities.CommunityModule
import me.santiagoreina.parcheu.api.graph.communities.inMemoryCommunityModule
import me.santiagoreina.parcheu.api.home.UnreadCounter
import me.santiagoreina.parcheu.api.notifications.center.NotificationCenterModule
import me.santiagoreina.parcheu.api.notifications.center.inMemoryNotificationCenter
import me.santiagoreina.parcheu.api.graph.inMemoryGraphModule
import me.santiagoreina.parcheu.api.chat.ChatModule
import me.santiagoreina.parcheu.api.chat.inMemoryChatModule
import me.santiagoreina.parcheu.api.simulation.SimulationModule
import me.santiagoreina.parcheu.api.simulation.inMemorySimulationModule
import me.santiagoreina.parcheu.api.platform.AppContainer
import me.santiagoreina.parcheu.api.matching.MatchingModule
import me.santiagoreina.parcheu.api.matching.inMemoryMatchingModule
import me.santiagoreina.parcheu.api.platform.notifier.NoopNotifier
import me.santiagoreina.parcheu.api.platform.notifier.Notifier
import me.santiagoreina.parcheu.api.social.introductions.InMemoryIntroductionRepository
import me.santiagoreina.parcheu.api.social.introductions.IntroductionModule
import me.santiagoreina.parcheu.api.social.introductions.IntroductionRepository
import me.santiagoreina.parcheu.api.social.introductions.introductionModule
import me.santiagoreina.parcheu.api.twin.TwinModule
import me.santiagoreina.parcheu.api.twin.inMemoryTwinModule
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.allDomains
import me.santiagoreina.parcheu.api.scenario.ScenarioModule
import me.santiagoreina.parcheu.api.scenario.inMemoryScenarioModule
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
    quota: QuotaModule = inMemoryQuotaModule(clock),
    config: AppConfig = testConfig(),
    ai: AiGateway = testGateway(),
    twin: TwinModule = inMemoryTwinModule(clock, ai),
    graph: GraphModule = inMemoryGraphModule(clock),
    chat: ChatModule = inMemoryChatModule(clock, graph.policy),
    simulation: SimulationModule = inMemorySimulationModule(clock, ai, quota, graph, chat = chat),
    notifier: Notifier = NoopNotifier,
    matching: MatchingModule = inMemoryMatchingModule(clock, quota, simulation, graph, notifier),
    safety: SafetyModule = inMemorySafetyModule(clock, chat, graph, matching, simulation = simulation),
    notifications: NotificationCenterModule = inMemoryNotificationCenter(clock, quota, graph),
    // Por defecto la campana de Inicio cuenta en la misma bandeja que C2 (E5.4).
    unread: UnreadCounter = UnreadCounter { notifications.service.unread(it) },
    // Presentaciones (E6.2): para que el fin de simulación avise solo a quien presenta, [simulation] debe
    // usar un `IntroductionSimulationNotifier` sobre este mismo [introductionStore] (IntroductionApiTest).
    introductionStore: IntroductionRepository = InMemoryIntroductionRepository(),
    introductions: IntroductionModule = introductionModule(introductionStore, simulation, graph, chat, quota, notifier, clock),
    invites: InviteModule = inMemoryInviteModule(clock, graph, simulation, notifier),
    // Solicitudes de conexión (E6.6): por defecto lee las decisiones en memoria de [simulation].
    connectRequests: ConnectRequestModule = inMemoryConnectRequestModule(simulation, graph, quota),
    // Billing (E7.1): para que `verify` dé Plus en el cupo, pasar también `quota` con `billing.entitlements`.
    billing: BillingModule = inMemoryBillingModule(clock),
    // Administración de reportes (E8.5): por defecto lee el repositorio de safety en memoria y suspende en [repo].
    admin: AdminModule = inMemoryAdminModule(repo, chat, safety, clock),
    // Comunidades (E6.4): por defecto sobre el grafo en memoria y los rasgos del gemelo en memoria.
    communities: CommunityModule = inMemoryCommunityModule(graph, ai, quota, clock, traitsOf = { twin.repository.state(it)?.traits }),
    // Moderación con IA (E8.6): por defecto con la misma pasarela [ai] y sin engancharse al chat.
    moderation: ModerationService = moderationService(ai, chat, safety, FakeTaskQueue(), clock, notifier),
    // Modo escenario (E4.8): ScenarioApiTest pasa uno con su propia cola y reloj.
    scenario: ScenarioModule = inMemoryScenarioModule(clock, ai, quota, graph, chat, simulation, notifier),
    // Último parámetro: los tests de E8.3 lo pasan como lambda final.
    account: (IdentityService) -> AccountModule = { identity -> inMemoryAccountModule(identity, graph, twin, clock) },
): AppContainer {
    val identity = IdentityService(repo, clock, legalTextVersion)
    val profile = ProfileService(identity, InMemoryProfileRepository(repo), clock)
    val accountModule = account(identity)
    return AppContainer(
        config = config,
        clock = clock,
        tokenVerifier = FakeTokenVerifier(adminUids = setOf("admin")),
        oidcVerifier = FakeOidcVerifier(TASKS_ACCOUNT),
        identity = identity,
        profile = profile,
        quota = quota,
        ai = ai,
        twin = twin,
        graph = graph,
        chat = chat,
        introductionStore = introductionStore,
        simulation = simulation,
        notifier = notifier,
        matching = matching,
        safety = safety,
        admin = admin,
        account = accountModule,
        notifications = notifications,
        introductions = introductions,
        unread = unread,
        billing = billing,
        invites = invites,
        communities = communities,
        connectRequests = connectRequests,
        moderation = moderation,
        scenario = scenario,
        domains = allDomains(
            identity, profile, quota, twin, graph, chat, simulation, matching, safety, accountModule, unread, notifications,
            invites, billing, config.env, introductions, admin, communities, scenario, connectRequests, moderation,
        ) + extra,
    )
}

/** Arranca el módulo con Fakes y devuelve un cliente que entiende el JSON del contrato. */
fun ApplicationTestBuilder.startApp(container: AppContainer = testContainer()): HttpClient {
    application { module(container) }
    return createClient { install(ContentNegotiation) { json(ContractJson) } }
}
