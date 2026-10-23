package me.santiagoreina.parcheu.api.simulation

import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Progress
import java.time.Instant
import java.util.UUID

/**
 * Persistencia del motor (arquitectura.md §4: `simulations`, `scenarios`, y lecturas de `users`,
 * `user_modes`, `blocks`, `twins` y `twin_memories`). [ExposedSimulationRepository] es la real;
 * [InMemorySimulationRepository] replica su semántica para pruebas.
 */
interface SimulationRepository {
    suspend fun participant(userId: UUID): Participant?

    /** ¿Hay un bloqueo entre [a] y [b] en cualquier sentido? */
    suspend fun blockedEitherWay(a: UUID, b: UUID): Boolean

    suspend fun twinProfile(userId: UUID): TwinProfile?

    /**
     * Memorias ACCEPTED de [userId] **solo del modo [mode]** (filtro en SQL), las más recientes primero.
     * `TwinPromptBuilder` vuelve a filtrar por modo: dos barreras (ADR-0032).
     */
    suspend fun acceptedMemories(userId: UUID, mode: Mode): List<TwinMemory>

    /** Escenarios del modo ordenados por id. */
    suspend fun scenarios(mode: Mode): List<ScenarioDef>

    suspend fun create(record: SimulationRecord)

    suspend fun find(id: UUID): SimulationRecord?

    /**
     * Simulaciones donde participa [userId], más recientes primero, desde el cursor (exclusivo). No incluye
     * las de una presentación (`INTRODUCTION`, E6.2): esas se ven solo por `/v1/introductions/{id}`.
     */
    suspend fun listFor(userId: UUID, before: SimulationCursor?, limit: Int): List<SimulationRecord>

    /**
     * Simulación DONE del mismo par (en cualquier orden) y modo, con las mismas versiones de gemelo
     * por persona, terminada después de [since]. Excluye [excluding].
     */
    suspend fun findCached(
        userA: UUID,
        versionA: Int,
        userB: UUID,
        versionB: Int,
        mode: Mode,
        since: Instant,
        excluding: UUID,
    ): SimulationRecord?

    /** QUEUED → RUNNING. Devuelve false si ya no estaba en QUEUED. */
    suspend fun markRunning(id: UUID): Boolean

    /**
     * Guarda un paso (turno o veredicto) solo si la fila sigue RUNNING y su transcripción tiene
     * exactamente [expectedSteps] pasos: dos entregas simultáneas de la misma tarea no duplican
     * turnos. Suma [usage] a los acumulados. Devuelve false si otro proceso ya avanzó.
     */
    suspend fun saveStep(id: UUID, expectedSteps: Int, transcript: StoredTranscript, progress: Progress, usage: UsageTotals): Boolean

    /** RUNNING/QUEUED → DONE con el resultado. Devuelve false si ya estaba terminada. */
    suspend fun markDone(
        id: UUID,
        score: Int,
        report: StoredReport,
        transcript: StoredTranscript,
        progress: Progress,
        cachedFrom: UUID?,
        provider: String?,
        usage: UsageTotals,
        now: Instant,
    ): Boolean

    /** → FAILED con el motivo. Devuelve false si ya estaba terminada. */
    suspend fun markFailed(id: UUID, error: String, now: Instant): Boolean
}

/** Cursor de `GET /v1/simulations`: `<epochMicros>_<uuid>` de la última fila de la página anterior. */
data class SimulationCursor(val createdAt: Instant, val id: UUID) {
    fun encode(): String = "${createdAt.epochSecond * 1_000_000 + createdAt.nano / 1_000}_$id"

    companion object {
        fun parse(raw: String): SimulationCursor? {
            val parts = raw.split('_')
            if (parts.size != 2) return null
            val micros = parts[0].toLongOrNull() ?: return null
            val id = runCatching { UUID.fromString(parts[1]) }.getOrNull() ?: return null
            return SimulationCursor(Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L), Math.floorMod(micros, 1_000_000L) * 1_000L), id)
        }
    }
}
