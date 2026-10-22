package me.santiagoreina.parcheu.api.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.platform.db.tables.Blocks
import me.santiagoreina.parcheu.api.platform.db.tables.ChatMembers
import me.santiagoreina.parcheu.api.platform.db.tables.ChatMessages
import me.santiagoreina.parcheu.api.platform.db.tables.Chats
import me.santiagoreina.parcheu.api.platform.db.tables.Consents
import me.santiagoreina.parcheu.api.platform.db.tables.Universities
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.contract.ChatStatus
import me.santiagoreina.parcheu.contract.ChatType
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
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Chat sobre Postgres (arquitectura.md §4 y §8.3). La idempotencia del envío la da la restricción
 * `unique (sender_id, client_id)` con `ON CONFLICT DO NOTHING`; el chat DIRECT único por par y modo,
 * un `pg_advisory_xact_lock` sobre la clave del par.
 */
class ExposedChatRepository(private val db: Database) : ChatRepository {

    private suspend fun <T> tx(block: JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) { transaction(db) { block() } }

    override suspend fun getOrCreateDirect(a: UUID, b: UUID, mode: Mode, now: Instant): CreatedChat = tx {
        val (low, high) = if (a.toString() < b.toString()) a to b else b to a
        // Serializa la búsqueda y el alta del mismo par y modo hasta el fin de la transacción.
        // La clave solo contiene UUIDs y el nombre del enum: no hay entrada del usuario en el SQL.
        exec("select pg_advisory_xact_lock(hashtextextended('chat-direct:$low:$high:${mode.name}', 0))")
        val existing = findDirect(low, high, mode)
        if (existing != null) {
            CreatedChat(loadChat(existing)!!, created = false)
        } else {
            CreatedChat(insertChat(NewChat(ChatType.DIRECT, mode, listOf(a, b), now)), created = true)
        }
    }

    override suspend fun create(chat: NewChat): ChatRecord = tx { insertChat(chat) }

    override suspend fun chat(chatId: UUID): ChatRecord? = tx { loadChat(chatId) }

    override suspend fun person(userId: UUID): PersonRow? = tx {
        Users.join(Universities, JoinType.LEFT, Users.universityId, Universities.id)
            .selectAll()
            .where { Users.id eq userId }
            .singleOrNull()
            ?.toPerson()
    }

    override suspend fun findByClientId(senderId: UUID, clientId: String): MessageRecord? = tx {
        ChatMessages.selectAll()
            .where { (ChatMessages.senderId eq senderId) and (ChatMessages.clientId eq clientId) }
            .singleOrNull()
            ?.toMessage()
    }

    override suspend fun setStatus(chatId: UUID, status: ChatStatus): ChatRecord? = tx {
        Chats.update({ Chats.id eq chatId }) { it[Chats.status] = status }
        loadChat(chatId)
    }

    override suspend fun blockedBetween(a: UUID, b: UUID): Boolean = tx {
        Blocks.selectAll().where {
            ((Blocks.blockerId eq a) and (Blocks.blockedId eq b)) or ((Blocks.blockerId eq b) and (Blocks.blockedId eq a))
        }.any()
    }

    override suspend fun blockedEitherWay(userId: UUID): Set<UUID> = tx {
        Blocks.selectAll()
            .where { (Blocks.blockerId eq userId) or (Blocks.blockedId eq userId) }
            .map { if (it[Blocks.blockerId] == userId) it[Blocks.blockedId] else it[Blocks.blockerId] }
            .toSet()
    }

    override suspend fun learningConsent(userId: UUID, mode: Mode): Boolean = tx {
        Consents.selectAll()
            .where { (Consents.userId eq userId) and (Consents.kind eq learningKind(mode)) }
            .orderBy(Consents.id to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.get(Consents.granted) ?: false
    }

    override suspend fun saveMessage(message: NewMessage): SavedMessage = tx {
        val id = UUID.randomUUID()
        val inserted = ChatMessages.insertIgnore {
            it[ChatMessages.id] = id
            it[chatId] = message.chatId
            it[senderId] = message.senderId
            it[text] = message.text
            it[learnable] = message.learnable
            it[hidden] = false
            it[clientId] = message.clientId
            it[createdAt] = message.createdAt.odt()
        }.insertedCount == 1
        if (inserted) {
            Chats.update({ Chats.id eq message.chatId }) { it[lastMessageAt] = message.createdAt.odt() }
        }
        val saved = ChatMessages.selectAll()
            .where { (ChatMessages.senderId eq message.senderId) and (ChatMessages.clientId eq message.clientId) }
            .single()
            .toMessage()
        SavedMessage(saved, inserted)
    }

    override suspend fun chatsOf(userId: UUID): List<ChatListRow> = tx {
        val chatIds = ChatMembers.selectAll().where { ChatMembers.userId eq userId }.map { it[ChatMembers.chatId] }
        if (chatIds.isEmpty()) return@tx emptyList()
        val chats = chatIds.mapNotNull { loadChat(it) }
        val otherIds = chats.flatMap { chat -> chat.members.map { it.userId } }.filter { it != userId }.distinct()
        val people = if (otherIds.isEmpty()) {
            emptyMap()
        } else {
            Users.join(Universities, JoinType.LEFT, Users.universityId, Universities.id)
                .selectAll()
                .where { Users.id inList otherIds }
                .associate { it[Users.id] to it.toPerson() }
        }
        chats.map { chat ->
            val me = chat.member(userId)!!
            val last = ChatMessages.selectAll()
                .where { ChatMessages.chatId eq chat.id }
                .orderBy(ChatMessages.createdAt to SortOrder.DESC, ChatMessages.id to SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.toMessage()
            var unreadWhere: Op<Boolean> = (ChatMessages.chatId eq chat.id) and (ChatMessages.hidden eq false) and
                ((ChatMessages.senderId neq userId) or ChatMessages.senderId.isNull())
            me.lastReadAt?.let { read -> unreadWhere = unreadWhere and (ChatMessages.createdAt greater read.odt()) }
            val unread = ChatMessages.selectAll().where(unreadWhere).count().toInt()
            val other = chat.members.firstOrNull { it.userId != userId }?.let { people[it.userId] }
            ChatListRow(chat, other, last, unread)
        }.sortedWith(compareByDescending<ChatListRow> { it.chat.lastMessageAt ?: it.chat.createdAt }.thenBy { it.chat.id })
    }

    override suspend fun markRead(chatId: UUID, userId: UUID, now: Instant): Boolean = tx {
        ChatMembers.update({ (ChatMembers.chatId eq chatId) and (ChatMembers.userId eq userId) }) {
            it[lastReadAt] = now.odt()
        } == 1
    }

    override suspend fun setMuted(chatId: UUID, userId: UUID, muted: Boolean): Boolean = tx {
        ChatMembers.update({ (ChatMembers.chatId eq chatId) and (ChatMembers.userId eq userId) }) {
            it[ChatMembers.muted] = muted
        } == 1
    }

    // --- internos (dentro de una transacción) ------------------------------------------------

    private fun findDirect(low: UUID, high: UUID, mode: Mode): UUID? {
        val candidates = Chats.join(ChatMembers, JoinType.INNER, Chats.id, ChatMembers.chatId)
            .selectAll()
            .where { (Chats.type eq ChatType.DIRECT) and (Chats.mode eq mode) and (ChatMembers.userId eq low) }
            .map { it[Chats.id] }
        if (candidates.isEmpty()) return null
        return ChatMembers.selectAll()
            .where { (ChatMembers.chatId inList candidates) and (ChatMembers.userId eq high) }
            .map { it[ChatMembers.chatId] }
            .minOrNull()
    }

    private fun insertChat(chat: NewChat): ChatRecord {
        val id = UUID.randomUUID()
        Chats.insert {
            it[Chats.id] = id
            it[type] = chat.type
            it[mode] = chat.mode
            it[scenarioId] = chat.scenarioId?.toShort()
            it[endsAt] = chat.endsAt?.odt()
            it[status] = ChatStatus.OPEN
            it[createdAt] = chat.createdAt.odt()
        }
        chat.members.distinct().forEach { member ->
            ChatMembers.insert {
                it[chatId] = id
                it[userId] = member
            }
        }
        return loadChat(id)!!
    }

    private fun loadChat(chatId: UUID): ChatRecord? {
        val row = Chats.selectAll().where { Chats.id eq chatId }.singleOrNull() ?: return null
        val members = ChatMembers.join(Users, JoinType.INNER, ChatMembers.userId, Users.id)
            .selectAll()
            .where { ChatMembers.chatId eq chatId }
            .orderBy(ChatMembers.userId to SortOrder.ASC)
            .map {
                ChatMemberRecord(
                    userId = it[ChatMembers.userId],
                    firebaseUid = it[Users.firebaseUid],
                    muted = it[ChatMembers.muted],
                    lastReadAt = it[ChatMembers.lastReadAt]?.toInstant(),
                )
            }
        return ChatRecord(
            id = row[Chats.id],
            type = row[Chats.type],
            mode = row[Chats.mode],
            scenarioId = row[Chats.scenarioId]?.toInt(),
            endsAt = row[Chats.endsAt]?.toInstant(),
            status = row[Chats.status],
            createdAt = row[Chats.createdAt].toInstant(),
            lastMessageAt = row[Chats.lastMessageAt]?.toInstant(),
            members = members,
        )
    }
}

private fun Instant.odt(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

private fun ResultRow.toMessage() = MessageRecord(
    id = this[ChatMessages.id],
    chatId = this[ChatMessages.chatId],
    senderId = this[ChatMessages.senderId],
    text = this[ChatMessages.text],
    learnable = this[ChatMessages.learnable],
    hidden = this[ChatMessages.hidden],
    clientId = this[ChatMessages.clientId],
    createdAt = this[ChatMessages.createdAt].toInstant(),
)

private fun ResultRow.toPerson() = PersonRow(
    id = this[Users.id],
    displayName = this[Users.displayName],
    photoUrl = this[Users.photoUrl],
    universityShort = this.getOrNull(Universities.shortName),
    visibility = this[Users.visibility],
    isDemo = this[Users.isDemo],
    deletedAt = this[Users.deletedAt]?.toInstant(),
)
