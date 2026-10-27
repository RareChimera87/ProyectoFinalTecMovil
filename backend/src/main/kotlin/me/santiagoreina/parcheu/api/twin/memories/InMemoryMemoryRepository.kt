package me.santiagoreina.parcheu.api.twin.memories

import me.santiagoreina.parcheu.api.twin.InMemoryTwinRepository
import me.santiagoreina.parcheu.contract.MemoryStatus
import me.santiagoreina.parcheu.contract.Mode
import java.time.Instant
import java.util.UUID

/**
 * Memorias en memoria para tests y `ENV=local` sin base. Los mensajes y los consentimientos se
 * siembran con [addMessage] y [setLearning]; aceptar una memoria sube la versión en [twins].
 */
class InMemoryMemoryRepository(private val twins: InMemoryTwinRepository? = null) : MemoryRepository {
    /** Un mensaje de chat tal como lo guardaría `chat_messages` (con el modo de su chat). */
    data class StoredMessage(
        val id: UUID,
        val chatId: UUID,
        val mode: Mode,
        val senderId: UUID?,
        val text: String,
        val learnable: Boolean,
        val hidden: Boolean,
        val createdAt: Instant,
    )

    private val lock = Any()
    private val messages = mutableListOf<StoredMessage>()
    private val learning = HashMap<UUID, MutableMap<Mode, Boolean>>()
    private val cursors = HashMap<UUID, Instant>()
    private val memories = LinkedHashMap<UUID, MemoryRecord>()
    private val deleted = HashSet<UUID>()

    fun addMessage(
        senderId: UUID?,
        chatId: UUID,
        mode: Mode,
        text: String,
        createdAt: Instant,
        learnable: Boolean = true,
        hidden: Boolean = false,
    ): StoredMessage = synchronized(lock) {
        StoredMessage(UUID.randomUUID(), chatId, mode, senderId, text, learnable, hidden, createdAt).also { messages += it }
    }

    /** Última fila de `consents` del tipo de aprendizaje de [mode]. */
    fun setLearning(userId: UUID, mode: Mode, granted: Boolean) = synchronized(lock) {
        learning.getOrPut(userId) { HashMap() }[mode] = granted
    }

    fun markDeleted(userId: UUID) = synchronized(lock) { deleted += userId }

    /** Inserta una memoria directamente (para pruebas de la API). */
    fun addMemory(userId: UUID, mode: Mode, text: String, createdAt: Instant, status: MemoryStatus = MemoryStatus.PENDING): MemoryRecord =
        synchronized(lock) {
            MemoryRecord(UUID.randomUUID(), userId, mode, text, null, status, createdAt, null).also { memories[it.id] = it }
        }

    fun all(userId: UUID): List<MemoryRecord> = synchronized(lock) { memories.values.filter { it.userId == userId } }

    override fun usersToLearn(after: UUID?, limit: Int): List<UUID> = synchronized(lock) {
        messages.asSequence()
            .filter { it.learnable && !it.hidden && it.senderId != null && it.senderId !in deleted }
            .filter { m -> cursors[m.senderId]?.let { m.createdAt > it } ?: true }
            .mapNotNull { it.senderId }
            .distinct()
            .filter { after == null || it > after }
            .sorted()
            .take(limit)
            .toList()
    }

    override fun learningModes(userId: UUID): Set<Mode> = synchronized(lock) {
        learning[userId].orEmpty().filterValues { it }.keys.toSet()
    }

    override fun cursor(userId: UUID): Instant? = synchronized(lock) { cursors[userId] }

    override fun learnableMessages(userId: UUID, since: Instant?, until: Instant, limit: Int): List<LearnableMessage> = synchronized(lock) {
        messages
            .filter { it.senderId == userId && it.learnable && !it.hidden }
            .filter { (since == null || it.createdAt > since) && it.createdAt <= until }
            .sortedWith(compareBy<StoredMessage> { it.createdAt }.thenBy { it.id })
            .take(limit)
            .map { LearnableMessage(it.id, it.chatId, it.mode, it.text, it.createdAt) }
    }

    override fun saveExtraction(
        userId: UUID,
        expectedCursor: Instant?,
        newCursor: Instant,
        memories: List<NewMemory>,
        now: Instant,
    ): Boolean = synchronized(lock) {
        if (userId in deleted || cursors[userId] != expectedCursor) return false
        memories.forEach { m ->
            val record = MemoryRecord(UUID.randomUUID(), userId, m.mode, m.text, m.sourceChatId, MemoryStatus.PENDING, now, null)
            this.memories[record.id] = record
        }
        cursors[userId] = newCursor
        true
    }

    override fun deletePending(userId: UUID, modes: Set<Mode>): Int = synchronized(lock) {
        val gone = memories.values.filter { it.userId == userId && it.status == MemoryStatus.PENDING && it.mode in modes }
        gone.forEach { memories.remove(it.id) }
        gone.size
    }

    override fun list(userId: UUID, status: MemoryStatus?, before: MemoryCursor?, limit: Int): List<MemoryRecord> = synchronized(lock) {
        memories.values
            .filter { it.userId == userId && (status == null || it.status == status) }
            .filter { before == null || it.createdAt < before.createdAt || (it.createdAt == before.createdAt && it.id < before.id) }
            .sortedWith(compareByDescending<MemoryRecord> { it.createdAt }.thenByDescending { it.id })
            .take(limit)
    }

    override fun decide(userId: UUID, memoryId: UUID, status: MemoryStatus, now: Instant): Decision? = synchronized(lock) {
        require(status != MemoryStatus.PENDING) { "Una decisión es ACCEPTED o REJECTED" }
        val record = memories[memoryId]?.takeIf { it.userId == userId && userId !in deleted } ?: return null
        if (record.status != MemoryStatus.PENDING) return Decision(record, changed = false)
        val decided = record.copy(status = status, decidedAt = now)
        memories[memoryId] = decided
        if (status == MemoryStatus.ACCEPTED) twins?.bumpVersion(userId)
        Decision(decided, changed = true)
    }
}
