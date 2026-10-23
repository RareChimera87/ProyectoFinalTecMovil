package me.santiagoreina.parcheu.api.simulation

import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Progress
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import java.time.Instant
import java.util.UUID

/**
 * Misma semántica que [ExposedSimulationRepository] en memoria (pruebas y contenedor por defecto).
 * Las pruebas siembran personas, gemelos, memorias, bloqueos y escenarios con los métodos `put*`.
 */
class InMemorySimulationRepository(scenarios: List<ScenarioDef> = DefaultScenarios.all) : SimulationRepository {
    private val lock = Any()
    private val participants = mutableMapOf<UUID, Participant>()
    private val twins = mutableMapOf<UUID, TwinProfile>()
    private val memories = mutableMapOf<UUID, MutableList<Pair<TwinMemory, Boolean>>>()
    private val blocks = mutableSetOf<Pair<UUID, UUID>>()
    private val scenarioDefs = scenarios.toMutableList()
    private val simulations = linkedMapOf<UUID, SimulationRecord>()

    fun putParticipant(p: Participant) = synchronized(lock) { participants[p.id] = p }

    fun putTwin(t: TwinProfile) = synchronized(lock) { twins[t.userId] = t }

    /** [accepted] = false simula una memoria PENDING o REJECTED (nunca debe llegar al prompt). */
    fun putMemory(userId: UUID, memory: TwinMemory, accepted: Boolean = true) =
        synchronized(lock) { memories.getOrPut(userId) { mutableListOf() }.add(0, memory to accepted) }

    fun block(blocker: UUID, blocked: UUID) = synchronized(lock) { blocks += blocker to blocked }

    fun all(): List<SimulationRecord> = synchronized(lock) { simulations.values.toList() }

    override suspend fun participant(userId: UUID): Participant? = synchronized(lock) { participants[userId] }

    override suspend fun blockedEitherWay(a: UUID, b: UUID): Boolean = synchronized(lock) { (a to b) in blocks || (b to a) in blocks }

    override suspend fun twinProfile(userId: UUID): TwinProfile? = synchronized(lock) { twins[userId] }

    override suspend fun acceptedMemories(userId: UUID, mode: Mode): List<TwinMemory> = synchronized(lock) {
        memories[userId].orEmpty().filter { (m, accepted) -> accepted && m.mode == mode }.map { it.first }
    }

    override suspend fun scenarios(mode: Mode): List<ScenarioDef> = synchronized(lock) { scenarioDefs.filter { it.mode == mode }.sortedBy { it.id } }

    override suspend fun create(record: SimulationRecord) = synchronized(lock) {
        check(record.id !in simulations) { "La simulación ${record.id} ya existe" }
        simulations[record.id] = record
    }

    override suspend fun find(id: UUID): SimulationRecord? = synchronized(lock) { simulations[id] }

    override suspend fun listFor(userId: UUID, before: SimulationCursor?, limit: Int): List<SimulationRecord> = synchronized(lock) {
        simulations.values
            .filter { (it.userA == userId || it.userB == userId) && it.origin != SimulationOrigin.INTRODUCTION }
            .sortedWith(compareByDescending<SimulationRecord> { it.createdAt }.thenByDescending { it.id })
            .filter { before == null || it.createdAt < before.createdAt || (it.createdAt == before.createdAt && it.id < before.id) }
            .take(limit)
    }

    override suspend fun findCached(
        userA: UUID,
        versionA: Int,
        userB: UUID,
        versionB: Int,
        mode: Mode,
        since: Instant,
        excluding: UUID,
    ): SimulationRecord? = synchronized(lock) {
        simulations.values
            .filter { it.id != excluding && it.mode == mode && it.status == SimulationStatus.DONE && it.score != null }
            .filter { it.finishedAt != null && it.finishedAt > since }
            .filter {
                (it.userA == userA && it.twinVersionA == versionA && it.userB == userB && it.twinVersionB == versionB) ||
                    (it.userA == userB && it.twinVersionA == versionB && it.userB == userA && it.twinVersionB == versionA)
            }
            .maxByOrNull { it.finishedAt!! }
    }

    override suspend fun markRunning(id: UUID): Boolean = synchronized(lock) {
        val r = simulations[id] ?: return false
        if (r.status != SimulationStatus.QUEUED) return false
        simulations[id] = r.copy(status = SimulationStatus.RUNNING)
        true
    }

    override suspend fun saveStep(id: UUID, expectedSteps: Int, transcript: StoredTranscript, progress: Progress, usage: UsageTotals): Boolean =
        synchronized(lock) {
            val r = simulations[id] ?: return false
            if (r.status != SimulationStatus.RUNNING || r.transcript.steps != expectedSteps) return false
            simulations[id] = r.copy(transcript = transcript, progress = progress, usage = r.usage + usage)
            true
        }

    override suspend fun markDone(
        id: UUID,
        score: Int,
        report: StoredReport,
        transcript: StoredTranscript,
        progress: Progress,
        cachedFrom: UUID?,
        provider: String?,
        usage: UsageTotals,
        now: Instant,
    ): Boolean = synchronized(lock) {
        val r = simulations[id] ?: return false
        if (r.status == SimulationStatus.DONE || r.status == SimulationStatus.FAILED) return false
        simulations[id] = r.copy(
            status = SimulationStatus.DONE,
            score = score,
            report = report,
            transcript = transcript,
            progress = progress,
            cachedFrom = cachedFrom,
            provider = provider ?: r.provider,
            usage = r.usage + usage,
            finishedAt = now,
        )
        true
    }

    override suspend fun markFailed(id: UUID, error: String, now: Instant): Boolean = synchronized(lock) {
        val r = simulations[id] ?: return false
        if (r.status == SimulationStatus.DONE || r.status == SimulationStatus.FAILED) return false
        simulations[id] = r.copy(status = SimulationStatus.FAILED, error = error, finishedAt = now)
        true
    }
}
