package me.santiagoreina.parcheu.api.identity.deletion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.platform.db.tables.ChatMembers
import me.santiagoreina.parcheu.api.platform.db.tables.ChatMessages
import me.santiagoreina.parcheu.api.platform.db.tables.Simulations
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Purga de una cuenta en Postgres (E8.3, arquitectura.md §10.2, ADR-0025). Todo ocurre en una sola
 * transacción y es idempotente: si la fila de `users` ya no existe no hay nada que hacer.
 *
 * Lo que la ley obliga a conservar (consentimientos, compras, reportes y las simulaciones que el otro
 * sigue viendo) pasa a una **lápida** propia de esta cuenta ([tombstoneUid]): un usuario sin datos,
 * borrado y oculto. Así, al final, el uuid de la persona no aparece en ninguna tabla y las llaves
 * foráneas siguen válidas.
 */
class ExposedAccountPurgeRepository(private val db: Database) : AccountPurgeRepository {
    private val log = LoggerFactory.getLogger(ExposedAccountPurgeRepository::class.java)

    private suspend fun <T> tx(block: JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) { transaction(db) { block() } }

    override suspend fun plan(userId: UUID): PurgePlan? = tx {
        val user = Users.select(Users.firebaseUid, Users.displayName, Users.deletedAt)
            .where { Users.id eq userId }
            .singleOrNull() ?: return@tx null
        if (user[Users.deletedAt] == null) return@tx null
        val simulations = Simulations.select(Simulations.id)
            .where { (Simulations.userA eq userId) or (Simulations.userB eq userId) }
            .map { it[Simulations.id] }
        val memberOf = ChatMembers.select(ChatMembers.chatId).where { ChatMembers.userId eq userId }.map { it[ChatMembers.chatId] }
        val own = ChatMessages.select(ChatMessages.id, ChatMessages.chatId)
            .where { ChatMessages.senderId eq userId }
            .groupBy({ it[ChatMessages.chatId] }, { it[ChatMessages.id] })
        PurgePlan(
            userId = userId,
            firebaseUid = user[Users.firebaseUid],
            displayName = user[Users.displayName],
            simulationIds = simulations,
            chats = (memberOf + own.keys).distinct().map { ChatPurge(it, own[it].orEmpty()) },
        )
    }

    override suspend fun purge(plan: PurgePlan, now: Instant) {
        tx {
            val sql = connection.connection as Connection
            val user = plan.userId
            // Candado de la fila: dos entregas simultáneas de la misma tarea no purgan a la vez.
            val exists = sql.query("select 1 from users where id = ? and deleted_at is not null for update", user)
            if (!exists) {
                log.info("Purga de {}: la cuenta ya no existe o no está borrada", user)
                return@tx
            }
            val tombstone = tombstoneFor(sql, plan, now)

            // 1. Reportes abiertos donde la persona reporta o es reportada: se resuelven antes de perder el contexto.
            sql.update(
                "update reports set status = 'RESOLVED', resolution = coalesce(resolution, '$RESOLUTION'), " +
                    "resolved_at = coalesce(resolved_at, ?) where status = 'OPEN' and (reporter_id = ? or target_id = ?)",
                Timestamp.from(now), user, user,
            )

            // 1b. Simulaciones en curso donde participa: FAILED y cupo devuelto a quien lo pagó. Si el motor sigue
            //     corriendo, su siguiente `saveStep`/`markDone` ya no aplica (exigen RUNNING) y no recrea nada.
            val stopped = sql.ids(
                "update simulations set status = 'FAILED', error = ?, finished_at = ? " +
                    "where status in ('QUEUED', 'RUNNING') and (user_a = ? or user_b = ? or requested_by = ?) returning id",
                SIMULATION_ERROR, Timestamp.from(now), user, user, user,
            )
            if (stopped.isNotEmpty()) {
                sql.update(
                    "update usage_events set refunded = true where kind = 'SIMULATION' and ref_id = any(?::text[])",
                    sql.createArrayOf("text", stopped.map { it.toString() }.toTypedArray()),
                )
            }

            // 2. Columnas jsonb con datos personales (PersonalJsonPaths), antes de mover las filas a la lápida.
            val values = mapOf(
                PurgeParam.USER to user,
                PurgeParam.USER_TEXT to user.toString(),
                PurgeParam.PATTERN to "%$user%",
                PurgeParam.HASH_PATTERN to "%${sha256Hex(user.toString())}%",
                PurgeParam.NAME to plan.displayName?.takeIf { it.isNotBlank() },
                PurgeParam.CHAT_ROUTES to sql.createArrayOf("text", plan.chats.map { "parcheu://chat/${it.chatId}" }.toTypedArray()),
            )
            JsonbPurgeRules.rules.forEach { rule -> sql.update(rule.sql, *rule.params.map { values[it] }.toTypedArray()) }

            // 3. Consentimientos: prueba de la autorización (Ley 1581). La tabla solo admite INSERT, así que
            //    se copian a la lápida y luego se borran los originales con la excepción del trigger (E0.4).
            sql.update(
                "insert into consents (user_id, kind, granted, text_version, created_at) " +
                    "select ?, kind, granted, text_version, created_at from consents where user_id = ? order by id",
                tombstone, user,
            )
            sql.update("set local parcheu.consents_purge = 'on'")
            sql.update("delete from consents where user_id = ?", user)
            sql.update("set local parcheu.consents_purge = 'off'")

            // 4. Lo que sobrevive pasa a la lápida: simulaciones del otro (solo puntaje y fecha), reportes y compras.
            listOf("user_a", "user_b", "requested_by").forEach { col ->
                sql.update("update simulations set $col = ? where $col = ?", tombstone, user)
            }
            listOf("reporter_id", "target_id", "resolved_by").forEach { col ->
                sql.update("update reports set $col = ? where $col = ?", tombstone, user)
            }
            // El token de una concesión de dev (`dev-grant:<uuid>`, E4.1) lleva el uuid: se reescribe con el de la lápida.
            sql.update(
                "update purchase_events set purchase_token = replace(purchase_token, ?, ?) where purchase_token like ?",
                user.toString(), tombstone.toString(), "%$user%",
            )
            sql.update(
                "update entitlements set user_id = ?, purchase_token = replace(purchase_token, ?, ?) where user_id = ?",
                tombstone, user.toString(), tombstone.toString(), user,
            )

            // 5. Mensajes propios en chats compartidos: sin autor ('Usuario eliminado'); los del otro no se tocan.
            sql.update(
                "update chat_messages set sender_id = null, client_id = null, learnable = false where sender_id = ?",
                user,
            )

            // 6. La fila de users: ON DELETE CASCADE borra perfil, modos, tokens FCM, gemelo, memorias, fila,
            //    matches, aristas, comunidades, invitaciones, presentaciones, avisos, cuotas y bloqueos;
            //    ai_usage.user_id queda en null (ON DELETE SET NULL).
            sql.update("delete from users where id = ?", user)
        }
    }

    override suspend fun stalledDeletions(deletedBefore: Instant, limit: Int): List<UUID> = tx {
        val sql = connection.connection as Connection
        sql.ids(
            "select id from users where deleted_at is not null and deleted_at < ? and firebase_uid not like 'deleted:%' " +
                "order by deleted_at limit ?",
            Timestamp.from(deletedBefore), limit,
        )
    }

    /** Lápida de la cuenta: una por persona (las simulaciones exigen `user_a <> user_b` y `entitlements` es por usuario). */
    private fun tombstoneFor(sql: Connection, plan: PurgePlan, now: Instant): UUID {
        val uid = tombstoneUid(plan.firebaseUid)
        val at = Timestamp.from(now)
        sql.update(
            "insert into users (firebase_uid, visibility, deleted_at, created_at, updated_at) values (?, 'HIDDEN', ?, ?, ?) " +
                "on conflict (firebase_uid) do nothing",
            uid, at, at, at,
        )
        sql.prepareStatement("select id from users where firebase_uid = ?").use { st ->
            st.setString(1, uid)
            st.executeQuery().use { rs ->
                check(rs.next()) { "No se pudo crear la lápida de la cuenta" }
                return rs.getObject(1, UUID::class.java)
            }
        }
    }

    companion object {
        /** `reports.resolution` de los reportes que se cierran porque una de las partes borró su cuenta. */
        const val RESOLUTION = "ACCOUNT_DELETED"

        /** `simulations.error` de las simulaciones en curso que se detienen al purgar (mismo texto que el motor). */
        const val SIMULATION_ERROR = "Una de las cuentas ya no existe"
    }
}

private fun Connection.update(statement: String, vararg params: Any?): Int =
    prepareStatement(statement).use { st ->
        params.forEachIndexed { i, value -> st.bind(i + 1, value) }
        st.executeUpdate()
    }

private fun Connection.query(statement: String, vararg params: Any?): Boolean =
    prepareStatement(statement).use { st ->
        params.forEachIndexed { i, value -> st.bind(i + 1, value) }
        st.executeQuery().use { it.next() }
    }

private fun Connection.ids(statement: String, vararg params: Any?): List<UUID> =
    prepareStatement(statement).use { st ->
        params.forEachIndexed { i, value -> st.bind(i + 1, value) }
        st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getObject(1, UUID::class.java)) } }
    }

private fun java.sql.PreparedStatement.bind(index: Int, value: Any?) {
    when (value) {
        null -> setNull(index, java.sql.Types.VARCHAR)
        is java.sql.Array -> setArray(index, value)
        else -> setObject(index, value)
    }
}
