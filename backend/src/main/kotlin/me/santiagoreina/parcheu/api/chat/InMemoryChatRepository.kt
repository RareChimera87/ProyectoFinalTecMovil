package me.santiagoreina.parcheu.api.chat

import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.contract.ChatStatus
import me.santiagoreina.parcheu.contract.ChatType
import me.santiagoreina.parcheu.contract.Mode
import java.time.Instant
import java.util.UUID

/**
 * [ChatRepository] en memoria con la misma semántica que [ExposedChatRepository] (idempotencia por
 * `(sender, clientId)`, un DIRECT por par y modo). Para pruebas de rutas y `ENV=local` sin base:
 * los usuarios, bloqueos y consentimientos se siembran con [addUser], [block] y [setLearning].
 */
class InMemoryChatRepository : ChatRepository {
    private data class Member(val muted: Boolean = false, val lastReadAt: Instant? = null)
    private data class Chat(
        val id: UUID,
        val type: ChatType,
        val mode: Mode,
        val scenarioId: Int?,
        val endsAt: Instant?,
        var status: ChatStatus,
        val createdAt: Instant,
        var lastMessageAt: Instant?,
        val members: LinkedHashMap<UUID, Member>,
    )

    private val lock = Any()
    private val people = mutableMapOf<UUID, PersonRow>()
    private val firebaseUids = mutableMapOf<UUID, String>()
    private val blocks = mutableSetOf<Pair<UUID, UUID>>()
    private val learning = mutableMapOf<Pair<UUID, Mode>, Boolean>()
    private val chats = linkedMapOf<UUID, Chat>()
    private val messages = mutableListOf<MessageRecord>()

    /** Todos los mensajes guardados (las pruebas comprueban que no haya duplicados). */
    val allMessages: List<MessageRecord> get() = synchronized(lock) { messages.toList() }

    fun addUser(person: PersonRow, firebaseUid: String) = synchronized(lock) {
        people[person.id] = person
        firebaseUids[person.id] = firebaseUid
    }

    fun block(blocker: UUID, blocked: UUID) = synchronized(lock) { blocks += blocker to blocked }

    fun unblock(blocker: UUID, blocked: UUID) = synchronized(lock) { blocks -= blocker to blocked }

    fun setLearning(userId: UUID, mode: Mode, granted: Boolean) = synchronized(lock) { learning[userId to mode] = granted }

    override suspend fun getOrCreateDirect(a: UUID, b: UUID, mode: Mode, now: Instant): CreatedChat = synchronized(lock) {
        val existing = chats.values.firstOrNull { it.type == ChatType.DIRECT && it.mode == mode && it.members.keys == setOf(a, b) }
        if (existing != null) {
            // Un DIRECT cerrado por un bloqueo ya levantado se reabre (el servicio ya descartó un bloqueo vigente).
            existing.status = ChatStatus.OPEN
            CreatedChat(existing.toRecord(), created = false)
        } else CreatedChat(insert(NewChat(ChatType.DIRECT, mode, listOf(a, b), now)), created = true)
    }

    override suspend fun findDirect(a: UUID, b: UUID, mode: Mode): ChatRecord? = synchronized(lock) {
        chats.values.firstOrNull { it.type == ChatType.DIRECT && it.mode == mode && it.members.keys == setOf(a, b) }?.toRecord()
    }

    override suspend fun create(chat: NewChat): ChatRecord = synchronized(lock) { insert(chat) }

    override suspend fun chat(chatId: UUID): ChatRecord? = synchronized(lock) { chats[chatId]?.toRecord() }

    override suspend fun person(userId: UUID): PersonRow? = synchronized(lock) { people[userId] }

    override suspend fun findByClientId(senderId: UUID, clientId: String): MessageRecord? = synchronized(lock) {
        messages.firstOrNull { it.senderId == senderId && it.clientId == clientId }
    }

    override suspend fun hideMessage(messageId: UUID): MessageRecord? = synchronized(lock) {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return null
        messages[index] = messages[index].copy(hidden = true)
        messages[index]
    }

    override suspend fun setStatus(chatId: UUID, status: ChatStatus): ChatRecord? = synchronized(lock) {
        chats[chatId]?.also { it.status = status }?.toRecord()
    }

    override suspend fun blockedBetween(a: UUID, b: UUID): Boolean = synchronized(lock) { (a to b) in blocks || (b to a) in blocks }

    override suspend fun blockedEitherWay(userId: UUID): Set<UUID> = synchronized(lock) {
        blocks.mapNotNull { (x, y) -> if (x == userId) y else if (y == userId) x else null }.toSet()
    }

    override suspend fun learningConsent(userId: UUID, mode: Mode): Boolean = synchronized(lock) { learning[userId to mode] ?: false }

    override suspend fun saveMessage(message: NewMessage): SavedMessage = synchronized(lock) {
        val existing = messages.firstOrNull { it.senderId == message.senderId && it.clientId == message.clientId }
        if (existing != null) return SavedMessage(existing, inserted = false)
        val record = MessageRecord(
            id = UUID.randomUUID(),
            chatId = message.chatId,
            senderId = message.senderId,
            text = message.text,
            learnable = message.learnable,
            hidden = false,
            clientId = message.clientId,
            createdAt = message.createdAt,
        )
        messages += record
        chats[message.chatId]?.lastMessageAt = message.createdAt
        SavedMessage(record, inserted = true)
    }

    override suspend fun chatsOf(userId: UUID): List<ChatListRow> = synchronized(lock) {
        chats.values.filter { userId in it.members }.map { chat ->
            val me = chat.members.getValue(userId)
            val own = messages.filter { it.chatId == chat.id }
            val last = own.maxWithOrNull(compareBy<MessageRecord> { it.createdAt }.thenBy { it.id })
            val unread = own.count { m ->
                !m.hidden && m.senderId != userId && (me.lastReadAt == null || m.createdAt.isAfter(me.lastReadAt))
            }
            val other = chat.members.keys.firstOrNull { it != userId }?.let { people[it] }
            ChatListRow(chat.toRecord(), other, last, unread)
        }.sortedWith(compareByDescending<ChatListRow> { it.chat.lastMessageAt ?: it.chat.createdAt }.thenBy { it.chat.id })
    }

    override suspend fun markRead(chatId: UUID, userId: UUID, now: Instant): Boolean = updateMember(chatId, userId) { it.copy(lastReadAt = now) }

    override suspend fun setMuted(chatId: UUID, userId: UUID, muted: Boolean): Boolean = updateMember(chatId, userId) { it.copy(muted = muted) }

    private fun updateMember(chatId: UUID, userId: UUID, change: (Member) -> Member): Boolean = synchronized(lock) {
        val members = chats[chatId]?.members ?: return false
        val member = members[userId] ?: return false
        members[userId] = change(member)
        true
    }

    private fun insert(chat: NewChat): ChatRecord {
        val members = LinkedHashMap<UUID, Member>()
        chat.members.distinct().sorted().forEach { members[it] = Member() }
        val created = Chat(UUID.randomUUID(), chat.type, chat.mode, chat.scenarioId, chat.endsAt, ChatStatus.OPEN, chat.createdAt, null, members)
        chats[created.id] = created
        return created.toRecord()
    }

    private fun Chat.toRecord() = ChatRecord(
        id = id,
        type = type,
        mode = mode,
        scenarioId = scenarioId,
        endsAt = endsAt,
        status = status,
        createdAt = createdAt,
        lastMessageAt = lastMessageAt,
        members = members.map { (userId, m) ->
            ChatMemberRecord(userId, firebaseUids[userId] ?: error("Usuario $userId sin sembrar en InMemoryChatRepository"), m.muted, m.lastReadAt)
        },
    )
}
