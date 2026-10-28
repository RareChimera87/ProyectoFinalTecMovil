package me.santiagoreina.parcheu.api.twin.memories

import me.santiagoreina.parcheu.api.platform.db.tables.ChatMessages
import me.santiagoreina.parcheu.api.platform.db.tables.Chats
import me.santiagoreina.parcheu.api.platform.db.tables.Consents
import me.santiagoreina.parcheu.api.platform.db.tables.TwinLearningCursor
import me.santiagoreina.parcheu.api.platform.db.tables.TwinMemories
import me.santiagoreina.parcheu.api.platform.db.tables.Twins
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.contract.ConsentKind
import me.santiagoreina.parcheu.contract.MemoryStatus
import me.santiagoreina.parcheu.contract.Mode
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.sql.Connection
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Memorias en Postgres. Lo que cambia el estado de aprendizaje de un usuario (guardar una extracción
 * o decidir una memoria) bloquea primero su fila de `users` (`SELECT ... FOR UPDATE`), igual que
 * `ExposedTwinRepository`, así que dos ejecuciones de la tarea o dos decisiones se serializan.
 */
class ExposedMemoryRepository(private val db: Database) : MemoryRepository {

    override fun usersToLearn(after: UUID?, limit: Int): List<UUID> = transaction(db) {
        val sql = connection.connection as Connection
        sql.prepareStatement(USERS_TO_LEARN_SQL).use { st ->
            st.setObject(1, after)
            st.setObject(2, after)
            st.setInt(3, limit)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getObject(1, UUID::class.java)) } }
        }
    }

    override fun learningModes(userId: UUID): Set<Mode> = transaction(db) { currentLearningModes(userId) }

    private fun currentLearningModes(userId: UUID): Set<Mode> {
        if (Users.select(Users.id).where { (Users.id eq userId) and Users.deletedAt.isNull() }.empty()) return emptySet()
        val kinds = Mode.entries.associateBy { learningKindOf(it) }
        return Consents.select(Consents.kind, Consents.granted)
            .where { (Consents.userId eq userId) and (Consents.kind inList kinds.keys) }
            .orderBy(Consents.id to SortOrder.DESC)
            .map { it[Consents.kind] to it[Consents.granted] }
            .distinctBy { it.first }
            .filter { it.second }
            .mapNotNull { kinds[it.first] }
            .toSet()
    }

    override fun cursor(userId: UUID): Instant? = transaction(db) {
        TwinLearningCursor.selectAll().where { TwinLearningCursor.userId eq userId }.singleOrNull()
            ?.get(TwinLearningCursor.lastMessageAt)?.toInstant()
    }

    override fun learnableMessages(userId: UUID, since: Instant?, until: Instant, limit: Int): List<LearnableMessage> = transaction(db) {
        var where: Op<Boolean> = (ChatMessages.senderId eq userId) and (ChatMessages.learnable eq true) and
            (ChatMessages.hidden eq false) and (ChatMessages.createdAt lessEq until.odt())
        since?.let { where = where and (ChatMessages.createdAt greater it.odt()) }
        // Una cuenta marcada como borrada (users.deleted_at) no aporta mensajes: nada de ella llega al prompt.
        (ChatMessages innerJoin Chats)
            .join(Users, JoinType.INNER, ChatMessages.senderId, Users.id)
            .select(ChatMessages.id, ChatMessages.chatId, Chats.mode, ChatMessages.text, ChatMessages.createdAt)
            .where { where and Users.deletedAt.isNull() }
            .orderBy(ChatMessages.createdAt to SortOrder.ASC, ChatMessages.id to SortOrder.ASC)
            .limit(limit)
            .map {
                LearnableMessage(
                    id = it[ChatMessages.id],
                    chatId = it[ChatMessages.chatId],
                    mode = it[Chats.mode],
                    text = it[ChatMessages.text],
                    createdAt = it[ChatMessages.createdAt].toInstant(),
                )
            }
    }

    override fun saveExtraction(
        userId: UUID,
        expectedCursor: Instant?,
        newCursor: Instant,
        memories: List<NewMemory>,
        now: Instant,
    ): Boolean = transaction(db) {
        if (!lockUser(userId)) return@transaction false
        val current = TwinLearningCursor.selectAll().where { TwinLearningCursor.userId eq userId }.singleOrNull()
            ?.get(TwinLearningCursor.lastMessageAt)?.toInstant()
        if (current != expectedCursor) return@transaction false
        val at = now.odt()
        // El consentimiento pudo revocarse mientras se llamaba al modelo: se relee bajo el bloqueo, se descartan
        // las memorias de modos ya no concedidos y se borran las PENDING de esos modos (arquitectura.md §10.1).
        val granted = currentLearningModes(userId)
        val revoked = Mode.entries.toSet() - granted
        if (revoked.isNotEmpty()) {
            TwinMemories.deleteWhere {
                (TwinMemories.userId eq userId) and (TwinMemories.status eq MemoryStatus.PENDING) and (TwinMemories.mode inList revoked)
            }
        }
        memories.filter { it.mode in granted }.forEach { memory ->
            TwinMemories.insert {
                it[TwinMemories.userId] = userId
                it[mode] = memory.mode
                it[text] = memory.text
                it[sourceChatId] = memory.sourceChatId
                it[status] = MemoryStatus.PENDING
                it[createdAt] = at
            }
        }
        TwinLearningCursor.upsert {
            it[TwinLearningCursor.userId] = userId
            it[lastMessageAt] = newCursor.odt()
        }
        true
    }

    override fun deletePending(userId: UUID, modes: Set<Mode>): Int {
        if (modes.isEmpty()) return 0
        return transaction(db) {
            TwinMemories.deleteWhere {
                (TwinMemories.userId eq userId) and (TwinMemories.status eq MemoryStatus.PENDING) and (TwinMemories.mode inList modes)
            }
        }
    }

    override fun list(userId: UUID, status: MemoryStatus?, before: MemoryCursor?, limit: Int): List<MemoryRecord> = transaction(db) {
        var where: Op<Boolean> = TwinMemories.userId eq userId
        status?.let { where = where and (TwinMemories.status eq it) }
        before?.let { c ->
            val at = c.createdAt.odt()
            where = where and ((TwinMemories.createdAt less at) or ((TwinMemories.createdAt eq at) and (TwinMemories.id less c.id)))
        }
        TwinMemories.selectAll().where { where }
            .orderBy(TwinMemories.createdAt to SortOrder.DESC, TwinMemories.id to SortOrder.DESC)
            .limit(limit)
            .map { it.toRecord() }
    }

    override fun decide(userId: UUID, memoryId: UUID, status: MemoryStatus, now: Instant): Decision? = transaction(db) {
        require(status != MemoryStatus.PENDING) { "Una decisión es ACCEPTED o REJECTED" }
        if (!lockUser(userId)) return@transaction null
        val row = TwinMemories.selectAll()
            .where { (TwinMemories.id eq memoryId) and (TwinMemories.userId eq userId) }
            .forUpdate()
            .singleOrNull() ?: return@transaction null
        val record = row.toRecord()
        if (record.status != MemoryStatus.PENDING) return@transaction Decision(record, changed = false)
        val at = now.odt()
        TwinMemories.update({ TwinMemories.id eq memoryId }) {
            it[TwinMemories.status] = status
            it[decidedAt] = at
        }
        if (status == MemoryStatus.ACCEPTED) {
            // Una memoria aceptada cambia lo que sabe el gemelo: nueva versión (invalida la caché de simulaciones).
            Twins.update({ Twins.userId eq userId }) {
                it[version] = Twins.version + 1
                it[updatedAt] = at
            }
        }
        Decision(record.copy(status = status, decidedAt = now), changed = true)
    }

    /** Bloquea la fila del usuario vivo; false si no existe o está borrado. */
    private fun lockUser(userId: UUID): Boolean =
        !Users.select(Users.id).where { (Users.id eq userId) and Users.deletedAt.isNull() }.forUpdate().empty()

    private fun ResultRow.toRecord() = MemoryRecord(
        id = this[TwinMemories.id],
        userId = this[TwinMemories.userId],
        mode = this[TwinMemories.mode],
        text = this[TwinMemories.text],
        sourceChatId = this[TwinMemories.sourceChatId],
        status = this[TwinMemories.status],
        createdAt = this[TwinMemories.createdAt].toInstant(),
        decidedAt = this[TwinMemories.decidedAt]?.toInstant(),
    )

    companion object {
        /**
         * Parámetros: 1-2 `after` (null = desde el principio), 3 límite. Un usuario entra si tiene un
         * mensaje propio aprendible y visible posterior a su cursor; el consentimiento vigente lo
         * comprueba la tarea (con el consentimiento revocado solo avanza el cursor).
         */
        internal val USERS_TO_LEARN_SQL = """
            select u.id
            from users u
            left join twin_learning_cursor c on c.user_id = u.id
            where u.deleted_at is null
              and (cast(? as uuid) is null or u.id > cast(? as uuid))
              and exists (
                select 1 from chat_messages m
                where m.sender_id = u.id
                  and m.learnable
                  and not m.hidden
                  and (c.last_message_at is null or m.created_at > c.last_message_at)
              )
            order by u.id
            limit ?
        """.trimIndent()

        private fun learningKindOf(mode: Mode): ConsentKind = when (mode) {
            Mode.AMISTAD -> ConsentKind.CHAT_LEARNING_AMISTAD
            Mode.PAREJA -> ConsentKind.CHAT_LEARNING_PAREJA
            Mode.ESTUDIO -> ConsentKind.CHAT_LEARNING_ESTUDIO
        }
    }
}

private fun Instant.odt(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)
