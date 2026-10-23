package me.santiagoreina.parcheu.api.simulation.report

import java.time.Instant
import java.util.UUID

/**
 * Persistencia del informe y de la decisión mutua (arquitectura.md §4: `report_unlocks` y
 * `connection_decisions`). [ExposedReportRepository] es la real; [InMemoryReportRepository] replica
 * su semántica (incluida la serialización de [decide]) para las pruebas.
 */
interface ReportRepository {
    /** ¿[userId] desbloqueó el informe completo de [simulationId] con su informe mensual? */
    suspend fun isUnlocked(userId: UUID, simulationId: UUID): Boolean

    /** Registra el desbloqueo. Idempotente: repetirlo no hace nada. */
    suspend fun recordUnlock(userId: UUID, simulationId: UUID, now: Instant)

    /** Decisiones ya tomadas sobre [simulationId]: `users.id` → `connect`. */
    suspend fun decisions(simulationId: UUID): Map<UUID, Boolean>

    /**
     * Guarda la decisión de [userId] **serializada por simulación** (en Postgres con
     * `SELECT ... FOR UPDATE` sobre la fila de `simulations`): de dos decisiones simultáneas, solo la
     * segunda ve las dos. Devuelve [DecideOutcome.AlreadyDecided] si ya había decidido.
     */
    suspend fun decide(simulationId: UUID, userId: UUID, connect: Boolean, now: Instant): DecideOutcome
}

sealed interface DecideOutcome {
    /** Se guardó; [all] son las decisiones vigentes después de guardarla (incluida la nueva). */
    data class Saved(val all: Map<UUID, Boolean>) : DecideOutcome

    data object AlreadyDecided : DecideOutcome
}

/** En memoria (pruebas y `ENV=local` sin base). Un candado global hace de `FOR UPDATE`. */
class InMemoryReportRepository : ReportRepository {
    private val lock = Any()
    private val unlocks = mutableSetOf<Pair<UUID, UUID>>()
    private val decided = mutableMapOf<UUID, MutableMap<UUID, Boolean>>()

    override suspend fun isUnlocked(userId: UUID, simulationId: UUID): Boolean =
        synchronized(lock) { (userId to simulationId) in unlocks }

    override suspend fun recordUnlock(userId: UUID, simulationId: UUID, now: Instant) {
        synchronized(lock) { unlocks += userId to simulationId }
    }

    override suspend fun decisions(simulationId: UUID): Map<UUID, Boolean> =
        synchronized(lock) { decided[simulationId]?.toMap() ?: emptyMap() }

    override suspend fun decide(simulationId: UUID, userId: UUID, connect: Boolean, now: Instant): DecideOutcome =
        synchronized(lock) {
            val forSim = decided.getOrPut(simulationId) { mutableMapOf() }
            if (userId in forSim) return DecideOutcome.AlreadyDecided
            forSim[userId] = connect
            DecideOutcome.Saved(forSim.toMap())
        }
}
