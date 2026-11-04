package me.santiagoreina.parcheu.api.safety

import me.santiagoreina.parcheu.api.graph.PersonRow
import java.time.Instant
import java.util.UUID

/**
 * Acceso a datos de seguridad (`blocks` y `reports`). [ExposedSafetyRepository] usa Postgres;
 * [InMemorySafetyRepository] replica la semántica para pruebas de rutas y `ENV=local`.
 *
 * El bloqueo vive en una sola tabla que leen el grafo, el chat, la fila de match y la simulación:
 * insertar la fila es lo que lo vuelve inmediato y transversal.
 */
interface SafetyRepository {
    /** Persona por id (incluye borradas, con `deletedAt`); null si no existe. */
    suspend fun person(userId: UUID): PersonRow?

    /** Inserta el bloqueo; false si ya existía (idempotente). */
    suspend fun block(blocker: UUID, blocked: UUID, now: Instant, wasConnected: Boolean = false): Boolean

    /** Quita el bloqueo de [blocker] a [blocked]; false si no existía. */
    suspend fun unblock(blocker: UUID, blocked: UUID): Boolean

    /** Bloqueos hechos por [blocker], más recientes primero (sin personas borradas). */
    suspend fun blocksOf(blocker: UUID): List<BlockRow>

    /** Los últimos [limit] mensajes del chat en orden cronológico (del más viejo al más nuevo). */
    suspend fun recentMessages(chatId: UUID, limit: Int): List<SnapshotMessage>

    /** Participantes de la simulación (`user_a`, `user_b`); null si no existe. */
    suspend fun simulationParticipants(simulationId: UUID): Pair<UUID, UUID>?

    /**
     * Texto del turno [turnIndex] de la simulación (índice plano sobre los turnos de todos los
     * escenarios, en orden); null si la simulación no tiene ese turno.
     */
    suspend fun simulationTurnText(simulationId: UUID, turnIndex: Int): String?

    /** Informe de la simulación (`simulations.report`) en texto plano, o null si aún no hay (reporte AI_CONTENT desde F8). */
    suspend fun simulationReportText(simulationId: UUID): String?

    suspend fun insertReport(report: NewReport)

    /**
     * `details` del reporte AUTO (moderación con IA) sobre [messageId], o null si no hay. Evita duplicados al
     * reintentar la tarea y deja saber qué se decidió (ocultar, ayuda) sin volver a llamar al LLM.
     */
    suspend fun autoReportDetails(messageId: UUID): String?
}
