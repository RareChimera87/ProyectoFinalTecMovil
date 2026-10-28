package me.santiagoreina.parcheu.api.premium

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.api.chat.InMemoryChatRepository
import me.santiagoreina.parcheu.api.chat.inMemoryChatModule
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.graph.communities.inMemoryCommunityModule
import me.santiagoreina.parcheu.api.identity.InMemoryIdentityRepository
import me.santiagoreina.parcheu.api.matching.InMemoryMatchingRepository
import me.santiagoreina.parcheu.api.matching.InMemoryScenario
import me.santiagoreina.parcheu.api.matching.MatchPerson
import me.santiagoreina.parcheu.api.matching.MatchingFlags
import me.santiagoreina.parcheu.api.matching.MatchingRepository
import me.santiagoreina.parcheu.api.matching.ScenarioChatOutcome
import me.santiagoreina.parcheu.api.matching.inMemoryMatchingModule
import me.santiagoreina.parcheu.api.notifications.center.InMemoryNotificationStore
import me.santiagoreina.parcheu.api.notifications.center.notificationCenterModule
import me.santiagoreina.parcheu.api.platform.AppContainer
import me.santiagoreina.parcheu.api.platform.auth.FakeTokenVerifier
import me.santiagoreina.parcheu.api.platform.notifier.FakeNotifier
import me.santiagoreina.parcheu.api.platform.notifier.NoopNotifier
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.safety.InMemorySafetyRepository
import me.santiagoreina.parcheu.api.safety.asReportedText
import me.santiagoreina.parcheu.api.safety.inMemorySafetyModule
import me.santiagoreina.parcheu.api.simulation.DefaultScenarios
import me.santiagoreina.parcheu.api.simulation.InMemorySimulationRepository
import me.santiagoreina.parcheu.api.simulation.SimFixture
import me.santiagoreina.parcheu.api.simulation.inMemorySimulationModule
import me.santiagoreina.parcheu.api.social.introductions.InMemoryIntroductionRepository
import me.santiagoreina.parcheu.api.social.introductions.IntroductionSimulationNotifier
import me.santiagoreina.parcheu.api.testContainer
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.contract.StartSimulation
import me.santiagoreina.parcheu.contract.Visibility
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Mundo en memoria donde **todas** las superficies premium tienen algo que mostrarle a `ana` (la
 * observadora). Con [plus] = false ana es gratis; con true tiene ParcheU+ (`grant-plus` de dev). La
 * auditoría (E7.4) compara las dos: lo que ParcheU+ recibe y gratis no.
 *
 * - **Grafo:** ana, beto, caro y dani forman una clique de Amistad (comunidad y sugerencias de Inicio
 *   con amigos en común); eva es amiga de beto en Estudio y no de ana (amiga de amigos); gabi y hugo
 *   son amigos de ana que no se conocen (se pueden presentar).
 * - **Simulación** ana → fran ya terminada ([franSimulation]) y fran quiere conectar (solicitud de
 *   conexión para ana, informe y transcripción).
 * - **Aviso** bloqueado (`locked`) en la bandeja de ana con beto como actor.
 * - **Fila:** ana e ivan están en el repositorio del match; el modo escenario está encendido y el
 *   repositorio registra si se permitió un escenario premium ([allowPremiumCalls]).
 * - **Escenarios:** a los de serie se suman [EXTRA_PREMIUM] escenarios premium de Amistad para que la
 *   prueba de `POST /v1/simulations` con ParcheU+ vea uno casi con certeza.
 */
class PremiumWorld(val plus: Boolean) {
    val identity = InMemoryIdentityRepository().also { repo -> UIDS.forEach { repo.seedAcceptedUser(it, TEST_LEGAL_VERSION) } }
    val sim = SimFixture(repo = InMemorySimulationRepository(SCENARIOS))
    val ids: Map<String, UUID> = UIDS.associateWith { uid -> sim.person(uid, id = identity.user(uid).id) }
    val push = FakeNotifier()
    val introductionStore = InMemoryIntroductionRepository()
    val chatRepo = InMemoryChatRepository().also { repo ->
        ids.forEach { (uid, id) -> repo.addUser(PersonRow(id, uid, null, "UIS", Visibility.ANONYMOUS, false, null), uid) }
    }
    val chat = inMemoryChatModule(FIXED_CLOCK, sim.graph.policy, push, chatRepo)
    val simulation = inMemorySimulationModule(
        FIXED_CLOCK, sim.ai, sim.quota, sim.graph, sim.repo, sim.queue, sim.mirror,
        IntroductionSimulationNotifier(introductionStore, sim.notifier, push),
        chat = chat, push = push,
    )
    val communities = inMemoryCommunityModule(sim.graph, sim.ai, sim.quota, FIXED_CLOCK, FakeTaskQueue())
    val notificationStore = InMemoryNotificationStore()
    val notifications = notificationCenterModule(notificationStore, FIXED_CLOCK, sim.quota, sim.graph)

    /** Valores de `allowPremium` con que el match pidió crear el chat SCENARIO. */
    val allowPremiumCalls = CopyOnWriteArrayList<Boolean>()
    val matchRepo = InMemoryMatchingRepository(SCENARIOS.map { InMemoryScenario(it.id, it.mode, it.isBasic, it.timerSec) })
    private val recordingMatchRepo: MatchingRepository = object : MatchingRepository by matchRepo {
        override suspend fun createScenarioChat(matchId: UUID, now: Instant, allowPremium: Boolean): ScenarioChatOutcome {
            allowPremiumCalls += allowPremium
            return matchRepo.createScenarioChat(matchId, now, allowPremium)
        }
    }
    val matching = inMemoryMatchingModule(
        FIXED_CLOCK, sim.quota, simulation, sim.graph, NoopNotifier, recordingMatchRepo,
        flags = MatchingFlags(demoUsersEnabled = true, scenarioModeEnabled = true),
    )

    /** Reportes y bloqueos en memoria (E8.1) sobre el grafo, el chat y las simulaciones de este mundo. */
    val safetyRepo = InMemorySafetyRepository(
        people = { id -> sim.graph.repository.person(id) },
        simulations = { id -> sim.repo.find(id)?.let { it.userA to it.userB } },
        turns = { id -> sim.repo.find(id)?.transcript?.scenarios?.flatMap { s -> s.turns.map { it.text } }.orEmpty() },
        reportText = { id -> sim.repo.find(id)?.report?.asReportedText() },
    )
    val safety = inMemorySafetyModule(FIXED_CLOCK, chat, sim.graph, matching, safetyRepo, simulation)

    /** Simulación DONE ana → fran (el motor corre en línea). */
    val franSimulation: String

    init {
        clique("ana", "beto", "caro", "dani")
        connect("beto", "eva", Mode.ESTUDIO)
        connect("ana", "gabi")
        connect("ana", "hugo")
        listOf("ana", "ivan").forEach { uid ->
            matchRepo.addPerson(
                MatchPerson(
                    id = id(uid), firebaseUid = "uid-$uid", activeModes = Mode.entries.toSet(), ageVerified = true, underage = false,
                    parejaConfirmed = true, isDemo = false, deleted = false, birthDate = LocalDate.of(2004, 3, 14),
                    universityShort = "UIS", interests = listOf("musica"),
                ),
            )
        }
        notificationStore.add(
            id("ana"),
            NotificationKinds.CONNECT_REQUEST,
            mapOf<String, JsonElement>(
                "title" to JsonPrimitive("1 persona quiere conectar contigo"),
                "body" to JsonPrimitive("Beto quiere conectar contigo"),
                "route" to JsonPrimitive("parcheu://notifications"),
                "actorId" to JsonPrimitive(id("beto").toString()),
                "locked" to JsonPrimitive(true),
            ),
            Instant.parse("2026-10-25T11:00:00Z"),
        )
        if (plus) sim.plus(id("ana"))
        franSimulation = runBlocking { simulation.service.start(id("ana"), StartSimulation(id("fran").toString(), Mode.AMISTAD)).id }
        runBlocking { communities.service.runWeekly() }
    }

    fun id(uid: String): UUID = ids.getValue(uid)

    fun connect(a: String, b: String, mode: Mode = Mode.AMISTAD) = sim.graphRepo.putEdge(id(a), id(b), mode, EdgeState.CONNECTED, 75)

    private fun clique(vararg uids: String) {
        uids.forEachIndexed { i, a -> uids.drop(i + 1).forEach { b -> connect(a, b) } }
    }

    /** Ids de los escenarios premium (no básicos). */
    val premiumScenarioIds: Set<Int> = SCENARIOS.filterNot { it.isBasic }.map { it.id }.toSet()

    fun container(): AppContainer = testContainer(
        repo = identity, quota = sim.quota, ai = sim.ai, graph = sim.graph, chat = chat, simulation = simulation,
        notifier = push, matching = matching, notifications = notifications, introductionStore = introductionStore,
        communities = communities, safety = safety,
    )

    companion object {
        val UIDS = listOf("ana", "beto", "caro", "dani", "eva", "fran", "gabi", "hugo", "ivan")

        const val EXTRA_PREMIUM = 12

        /** Los de serie más [EXTRA_PREMIUM] premium de Amistad (copias de 'Un favor incómodo'). */
        val SCENARIOS = DefaultScenarios.all.let { all ->
            val template = all.first { it.mode == Mode.AMISTAD && !it.isBasic }
            all + (1..EXTRA_PREMIUM).map { n -> template.copy(id = 100 + n, slug = "${template.slug}-$n", title = "${template.title} $n") }
        }
    }
}

fun HttpRequestBuilder.auth(uid: String) = header("Authorization", "Bearer ${FakeTokenVerifier.tokenFor(uid)}")

/** Cliente HTTP y mundo que recibe cada sonda de [PremiumEndpoints]. */
class PremiumProbe(val world: PremiumWorld, val client: HttpClient)
