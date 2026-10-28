package me.santiagoreina.parcheu.api.identity.retention

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Reglas de retención sobre Postgres (E8.8). Cada regla es un `update`/`delete` con un límite estricto
 * (`< cutoff`) en su propia transacción: repetirla no encuentra nada que cambiar.
 */
class ExposedRetentionRepository(private val db: Database) : RetentionRepository {
    private suspend fun <T> tx(block: (Connection) -> T): T =
        withContext(Dispatchers.IO) { transaction(db) { block(connection()) } }

    private fun JdbcTransaction.connection(): Connection = connection.connection as Connection

    override suspend fun deleteNotificationsBefore(cutoff: Instant): Int =
        tx { it.update("delete from notifications where created_at < ?", cutoff.ts()) }

    override suspend fun anonymizeAiUsageBefore(cutoff: Instant): Int =
        tx { it.update("update ai_usage set user_id = null where user_id is not null and created_at < ?", cutoff.ts()) }

    override suspend fun clearReportSnapshotsResolvedBefore(cutoff: Instant): Int = tx {
        it.update(
            "update reports set snapshot = null where snapshot is not null and status = 'RESOLVED' and resolved_at < ?",
            cutoff.ts(),
        )
    }

    override suspend fun clearTranscriptsBefore(cutoff: Instant): Int =
        tx { it.update("update simulations set transcript = null where transcript is not null and created_at < ?", cutoff.ts()) }

    override suspend fun deleteMatchQueueExpiredBefore(cutoff: Instant): Int =
        tx { it.update("delete from match_queue where expires_at < ?", cutoff.ts()) }

    override suspend fun deleteExpiredMatchesBefore(cutoff: Instant): Int =
        tx { it.update("delete from matches where status = 'EXPIRED' and decide_by < ?", cutoff.ts()) }

    /**
     * Un chat SCENARIO se cierra en `ends_at` (la hora programada) o, sin ella, con su último mensaje.
     * Un chat OPEN sin `ends_at` no se da por cerrado.
     */
    override suspend fun closedScenarioChats(cutoff: Instant, limit: Int): List<UUID> = tx { sql ->
        sql.prepareStatement(
            "select id from chats where type = 'SCENARIO' and (status = 'CLOSED' or ends_at is not null) " +
                "and coalesce(ends_at, last_message_at, created_at) < ? order by id limit ?",
        ).use { st ->
            st.setTimestamp(1, cutoff.ts())
            st.setInt(2, limit)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getObject(1, UUID::class.java)) } }
        }
    }

    override suspend fun deleteChats(ids: List<UUID>): Int {
        if (ids.isEmpty()) return 0
        return tx { sql ->
            val array = sql.createArrayOf("uuid", ids.toTypedArray())
            // `matches.chat_id` no tiene ON DELETE; los reportes lo sueltan solos (SET NULL) y los mensajes caen por CASCADE.
            sql.prepareStatement("update matches set chat_id = null where chat_id = any(?)").use { it.bindArray(array) }
            sql.prepareStatement("delete from chats where id = any(?)").use { it.bindArray(array) }
        }
    }

    private fun PreparedStatement.bindArray(array: java.sql.Array): Int {
        setArray(1, array)
        return executeUpdate()
    }

    private fun Connection.update(statement: String, cutoff: Timestamp): Int =
        prepareStatement(statement).use { st ->
            st.setTimestamp(1, cutoff)
            st.executeUpdate()
        }

    private fun Instant.ts(): Timestamp = Timestamp.from(this)
}
