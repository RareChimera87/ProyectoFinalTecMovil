package me.santiagoreina.parcheu.api.demo

import kotlinx.coroutines.CancellationException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.UUID

/** Lo que borró `purge-demo` (para el log y para limpiar los espejos de Firestore). */
data class PurgeResult(
    val users: Int,
    val firebaseUids: List<String>,
    val chatIds: List<UUID>,
    val simulationIds: List<UUID>,
)

/**
 * Espejos de Firestore que también hay que limpiar (best effort: Postgres manda). En local y en las pruebas
 * no hay Firestore y se usa [NONE].
 */
interface DemoMirrorCleaner {
    suspend fun clean(result: PurgeResult)

    companion object {
        val NONE = object : DemoMirrorCleaner {
            override suspend fun clean(result: PurgeResult) = Unit
        }
    }
}

/**
 * `purge-demo` (ADR-0027, antes de la 1.0): borra a todos los usuarios `is_demo` y **todo** lo que los
 * referencia, también lo que por diseño sobrevive al borrado de una persona (`consents`, `simulations`,
 * `reports`, `entitlements`). Lo de los testers con un demo se va con él: matches, simulaciones e informes,
 * decisiones, chats (con los mensajes del tester), presentaciones, avisos cuyo `payload` nombra al demo y
 * reportes. Los rasgos y memorias del tester se quedan (son suyos); solo se suelta `source_chat_id`.
 *
 * Una sola transacción, en orden de dependencias; al final un `DELETE FROM users` deja que las llaves foráneas
 * en cascada limpien el resto. Idempotente: sin demos no hace nada.
 */
class DemoPurger(private val db: Database, private val mirrors: DemoMirrorCleaner = DemoMirrorCleaner.NONE) {
    private val log = LoggerFactory.getLogger(DemoPurger::class.java)

    suspend fun purge(): PurgeResult {
        val result = transaction(db) { purgeInTransaction() }
        log.info(
            "purge-demo: {} demos, {} chats y {} simulaciones borrados",
            result.users, result.chatIds.size, result.simulationIds.size,
        )
        try {
            mirrors.clean(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("purge-demo: Postgres quedó limpio pero falló la limpieza de Firestore: {}", e.message)
        }
        return result
    }

    private fun JdbcTransaction.purgeInTransaction(): PurgeResult {
        exec("create temp table demo_users on commit drop as select id, firebase_uid from users where is_demo")
        exec(
            """
            create temp table demo_sims on commit drop as
            select id from simulations
            where user_a in (select id from demo_users) or user_b in (select id from demo_users)
               or requested_by in (select id from demo_users)
            """.trimIndent(),
        )
        exec(
            """
            create temp table demo_chats on commit drop as
            select distinct chat_id as id from chat_members where user_id in (select id from demo_users)
            union
            select distinct chat_id from chat_messages where sender_id in (select id from demo_users)
            """.trimIndent(),
        )
        val uids = mutableListOf<String>()
        exec("select firebase_uid from demo_users order by firebase_uid") { rs -> while (rs.next()) uids += rs.getString(1) }
        val chats = ids("select id from demo_chats")
        val sims = ids("select id from demo_sims")
        if (uids.isEmpty()) return PurgeResult(0, emptyList(), emptyList(), emptyList())

        PURGE_STATEMENTS.forEach { exec(it.trimIndent()) }
        // `consents` solo admite INSERT salvo con esta bandera local a la transacción (V1, ADR-0017).
        exec("set local parcheu.consents_purge = 'on'")
        exec("delete from consents where user_id in (select id from demo_users)")
        exec("delete from users where id in (select id from demo_users)")
        return PurgeResult(uids.size, uids, chats, sims)
    }

    private fun JdbcTransaction.ids(sql: String): List<UUID> {
        val out = mutableListOf<UUID>()
        exec(sql) { rs -> while (rs.next()) out += rs.getObject(1, UUID::class.java) }
        return out
    }

    companion object {
        /**
         * Orden de dependencias: primero lo que apunta a simulaciones y chats demo (sin `ON DELETE CASCADE`),
         * luego las simulaciones y los chats, y lo que sobrevive a un usuario. El resto cae en cascada con `users`.
         */
        private val PURGE_STATEMENTS = listOf(
            """
            delete from matches
            where user_a in (select id from demo_users) or user_b in (select id from demo_users)
               or simulation_id in (select id from demo_sims) or chat_id in (select id from demo_chats)
            """,
            """
            delete from introductions
            where introducer_id in (select id from demo_users) or user_a in (select id from demo_users)
               or user_b in (select id from demo_users) or simulation_id in (select id from demo_sims)
            """,
            """
            delete from intro_requests
            where requester_id in (select id from demo_users) or via_user_id in (select id from demo_users)
               or target_user_id in (select id from demo_users)
            """,
            """
            delete from reports
            where reporter_id in (select id from demo_users) or target_id in (select id from demo_users)
               or resolved_by in (select id from demo_users) or simulation_id in (select id from demo_sims)
               or chat_id in (select id from demo_chats)
            """,
            "update edges set last_simulation_id = null where last_simulation_id in (select id from demo_sims)",
            "delete from report_unlocks where simulation_id in (select id from demo_sims)",
            "delete from connection_decisions where simulation_id in (select id from demo_sims)",
            "delete from twin_feedback where simulation_id in (select id from demo_sims)",
            "update simulations set cached_from = null where cached_from in (select id from demo_sims)",
            "delete from simulations where id in (select id from demo_sims)",
            "update twin_memories set source_chat_id = null where source_chat_id in (select id from demo_chats)",
            // Mensajes, miembros y `reports.chat_id` caen en cascada (o se anulan) con el chat.
            "delete from chats where id in (select id from demo_chats)",
            // Avisos de testers que nombran al demo (p. ej. NEW_CONNECTION con `actorId`).
            """
            delete from notifications n
            where exists (select 1 from demo_users d where position(d.id::text in n.payload::text) > 0)
            """,
            "delete from entitlements where user_id in (select id from demo_users)",
        )
    }
}
