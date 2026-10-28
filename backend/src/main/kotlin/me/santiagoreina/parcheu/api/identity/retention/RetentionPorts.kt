package me.santiagoreina.parcheu.api.identity.retention

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Postgres: cada método aplica una regla y devuelve cuántas filas purgó o anonimizó. Todos son idempotentes. */
interface RetentionRepository {
    suspend fun deleteNotificationsBefore(cutoff: Instant): Int

    /** Pone `user_id = null` en `ai_usage` anterior a [cutoff]; no toca `ai_usage_daily`. */
    suspend fun anonymizeAiUsageBefore(cutoff: Instant): Int

    /** Pone `snapshot = null` en reportes resueltos antes de [cutoff]. */
    suspend fun clearReportSnapshotsResolvedBefore(cutoff: Instant): Int

    /** Pone `transcript = null` en simulaciones creadas antes de [cutoff]. */
    suspend fun clearTranscriptsBefore(cutoff: Instant): Int

    suspend fun deleteMatchQueueExpiredBefore(cutoff: Instant): Int

    /** Borra los matches `EXPIRED` cuyo plazo de decisión terminó antes de [cutoff]. */
    suspend fun deleteExpiredMatchesBefore(cutoff: Instant): Int

    /** Ids de chats SCENARIO cerrados antes de [cutoff] (hasta [limit]). */
    suspend fun closedScenarioChats(cutoff: Instant, limit: Int): List<UUID>

    /** Borra los chats (con miembros y mensajes por cascada) y suelta la referencia desde `matches`. */
    suspend fun deleteChats(ids: List<UUID>): Int
}

/** Firestore: cada método borra lo vencido y devuelve cuántos documentos existían. Todos son idempotentes. */
interface RetentionMirror {
    /** Borra `simulations/{id}` cuya última publicación (`updatedAt`) es anterior a [cutoff]. */
    suspend fun deleteSimulationsBefore(cutoff: Instant): Int

    /** Borra `matchTickets/{uid}` con `expiresAt` anterior a [cutoff]. */
    suspend fun deleteMatchTicketsExpiredBefore(cutoff: Instant): Int

    /** Borra `chats/{id}` y su subcolección `messages`. */
    suspend fun deleteChats(ids: List<UUID>): Int
}

/** Sin Firestore (`ENV=local` y pruebas que no lo miran). */
object NoopRetentionMirror : RetentionMirror {
    override suspend fun deleteSimulationsBefore(cutoff: Instant) = 0

    override suspend fun deleteMatchTicketsExpiredBefore(cutoff: Instant) = 0

    override suspend fun deleteChats(ids: List<UUID>) = 0
}

/** Sin Postgres (pruebas de rutas): no hay nada que purgar. */
object NoopRetentionRepository : RetentionRepository {
    override suspend fun deleteNotificationsBefore(cutoff: Instant) = 0

    override suspend fun anonymizeAiUsageBefore(cutoff: Instant) = 0

    override suspend fun clearReportSnapshotsResolvedBefore(cutoff: Instant) = 0

    override suspend fun clearTranscriptsBefore(cutoff: Instant) = 0

    override suspend fun deleteMatchQueueExpiredBefore(cutoff: Instant) = 0

    override suspend fun deleteExpiredMatchesBefore(cutoff: Instant) = 0

    override suspend fun closedScenarioChats(cutoff: Instant, limit: Int) = emptyList<UUID>()

    override suspend fun deleteChats(ids: List<UUID>) = 0
}

/** Registra los límites y chats pedidos al espejo para que las pruebas los verifiquen. */
class RecordingRetentionMirror : RetentionMirror {
    val simulationCutoffs = CopyOnWriteArrayList<Instant>()
    val ticketCutoffs = CopyOnWriteArrayList<Instant>()
    val deletedChats = CopyOnWriteArrayList<UUID>()

    /** Si no es null, todas las operaciones lanzan este error (simula una caída de Firestore). */
    @Volatile var failWith: Throwable? = null

    override suspend fun deleteSimulationsBefore(cutoff: Instant): Int {
        failWith?.let { throw it }
        simulationCutoffs += cutoff
        return 0
    }

    override suspend fun deleteMatchTicketsExpiredBefore(cutoff: Instant): Int {
        failWith?.let { throw it }
        ticketCutoffs += cutoff
        return 0
    }

    override suspend fun deleteChats(ids: List<UUID>): Int {
        failWith?.let { throw it }
        deletedChats += ids
        return ids.size
    }
}
