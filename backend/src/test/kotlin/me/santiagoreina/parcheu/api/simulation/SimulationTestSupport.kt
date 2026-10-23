package me.santiagoreina.parcheu.api.simulation

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import me.santiagoreina.parcheu.api.FIXED_CLOCK
import me.santiagoreina.parcheu.api.ai.AI_DAY
import me.santiagoreina.parcheu.api.ai.FakeLlmClient
import me.santiagoreina.parcheu.api.ai.InMemoryAiUsageStore
import me.santiagoreina.parcheu.api.ai.LlmRequest
import me.santiagoreina.parcheu.api.ai.testGateway
import me.santiagoreina.parcheu.api.graph.InMemoryGraphRepository
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.graph.inMemoryGraphModule
import me.santiagoreina.parcheu.api.platform.tasks.FakeTaskQueue
import me.santiagoreina.parcheu.api.quota.ChargeResult
import me.santiagoreina.parcheu.api.quota.InMemoryDevEntitlementService
import me.santiagoreina.parcheu.api.quota.InMemoryUsageRepository
import me.santiagoreina.parcheu.api.quota.QuotaWindow
import me.santiagoreina.parcheu.api.quota.UsageRepository
import me.santiagoreina.parcheu.api.quota.inMemoryQuotaModule
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.contract.Visibility
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** `usage_events` en memoria que además deja ver el estado `refunded` de cada `ref_id`. */
class RecordingUsageRepository(private val delegate: InMemoryUsageRepository = InMemoryUsageRepository()) : UsageRepository by delegate {
    private val state = ConcurrentHashMap<String, Boolean>()
    val charges = CopyOnWriteArrayList<String>()

    /** `refunded` del uso con ese `ref_id`, o null si no existe. */
    fun refunded(refId: String): Boolean? = state[refId]

    override fun charge(userId: UUID, kind: QuotaKind, refId: String, window: QuotaWindow, limit: Int, now: Instant): ChargeResult {
        val result = delegate.charge(userId, kind, refId, window, limit, now)
        if (result == ChargeResult.Charged) {
            charges += refId
            state[refId] = false
        }
        return result
    }

    override fun refund(kind: QuotaKind, refId: String): Boolean = delegate.refund(kind, refId).also { if (it) state[refId] = true }
}

/** Veredicto JSON válido para el juez Fake. */
fun judgeJson(affinity: Int = 80): JsonObject = buildJsonObject {
    put("afinidad", affinity)
    putJsonArray("dimensiones") {
        listOf("humor", "planes", "valores", "comunicacion", "ritmo", "organizacion", "responsabilidad", "horarios").forEach { name ->
            addJsonObject {
                put("nombre", name)
                put("puntaje", affinity)
                put("nota", "Nota de $name")
            }
        }
    }
    putJsonArray("porQue") {
        add("Planean parecido")
        add("Se escuchan")
        add("Mismo humor")
    }
    putJsonArray("dondeChocarian") {
        add("Plata")
        add("Horarios")
    }
    put("resumen", "Encajan bien.")
}

/**
 * Mundo en memoria para el motor: FakeLlm que captura cada request, cuotas con `refunded` visible,
 * grafo, espejo y avisos que registran. [textReply] decide el turno a partir del request.
 */
class SimFixture(
    var textReply: (LlmRequest) -> String = { "Listo, me parece bien el plan." },
    var judgeReply: (LlmRequest) -> JsonObject = { judgeJson() },
    val repo: InMemorySimulationRepository = InMemorySimulationRepository(),
    val usage: RecordingUsageRepository = RecordingUsageRepository(),
    val graphRepo: InMemoryGraphRepository = InMemoryGraphRepository(),
    /** false: la tarea `simulate` queda encolada y se ejecuta llamando a la ruta interna. */
    inline: Boolean = true,
    /** `DEMO_USERS_ENABLED` de la simulación (E4.9). */
    demoUsersEnabled: Boolean = true,
) {
    val requests = CopyOnWriteArrayList<LlmRequest>()
    val fake = FakeLlmClient(
        textResponder = { requests += it; textReply(it) },
        jsonResponder = { r, _ -> requests += r; judgeReply(r) },
    )
    val aiUsage = InMemoryAiUsageStore()
    val ai = testGateway(fake, aiUsage)
    val entitlements = InMemoryDevEntitlementService()
    val quota = inMemoryQuotaModule(FIXED_CLOCK, usage = usage, entitlements = entitlements)
    val graph = inMemoryGraphModule(FIXED_CLOCK, graphRepo)
    val mirror = RecordingSimulationMirror()
    val notifier = RecordingSimulationNotifier()
    val queue = FakeTaskQueue()
    val module = inMemorySimulationModule(FIXED_CLOCK, ai, quota, graph, repo, queue, mirror, notifier, runInline = inline, demoUsersEnabled = demoUsersEnabled)

    /** Agota el presupuesto del día de la pasarela de pruebas (10 USD). */
    fun exhaustBudget() = aiUsage.seedSpent(AI_DAY, 10_000_000)

    fun plus(userId: UUID) = entitlements.grantPlus(userId, 30, FIXED_CLOCK.instant())

    /** Persona con gemelo listo, modos activos y fila en el grafo. */
    fun person(
        name: String,
        modes: Set<Mode> = Mode.entries.toSet(),
        traits: List<Double> = List(6) { 0.5 },
        persona: String = "Persona de $name: le gusta salir y organizarse.",
        id: UUID = UUID.nameUUIDFromBytes("sim:$name".toByteArray()),
        ageVerified: Boolean = true,
        version: Int = 1,
        isDemo: Boolean = false,
    ): UUID {
        repo.putParticipant(Participant(id, "uid-$name", modes, ageVerified, ageVerified, isDemo = isDemo, deleted = false))
        repo.putTwin(TwinProfile(id, version, traits, persona, personaReady = true))
        graphRepo.addPerson(PersonRow(id, name, null, "UIS", Visibility.ANONYMOUS, isDemo, null))
        return id
    }

    fun turnRequests() = requests.filter { it.maxOutputTokens == TwinAgent.TURN_OUTPUT_CEILING }

    fun judgeRequests() = requests.filter { it.maxOutputTokens != TwinAgent.TURN_OUTPUT_CEILING }
}
