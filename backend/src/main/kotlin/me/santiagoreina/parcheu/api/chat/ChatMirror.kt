package me.santiagoreina.parcheu.api.chat

import me.santiagoreina.parcheu.contract.ChatStatus
import me.santiagoreina.parcheu.contract.ChatType
import me.santiagoreina.parcheu.contract.Mode
import java.time.Instant
import java.util.UUID

/** Documento `chats/{chatId}` de arquitectura.md §8.2. [members] son uids de Firebase. */
data class ChatDoc(
    val id: UUID,
    val members: List<String>,
    val type: ChatType,
    val mode: Mode,
    val status: ChatStatus,
    val endsAt: Instant?,
)

/**
 * Documento `chats/{chatId}/messages/{msgId}` de arquitectura.md §8.2 y de las reglas de E0.8:
 * `senderUid` (uid de Firebase; nulo si [system]), `system`, `text`, `createdAt` y `hidden`.
 */
data class MessageDoc(
    val id: UUID,
    val senderUid: String?,
    val system: Boolean,
    val text: String,
    val createdAt: Instant,
    val hidden: Boolean,
)

/**
 * Espejo de solo lectura para los clientes (ADR-0004). Solo el backend escribe; las escrituras usan
 * el id de Postgres como id del documento, así que repetirlas no crea duplicados.
 */
interface ChatMirror {
    suspend fun upsertChat(doc: ChatDoc)

    suspend fun upsertMessage(chatId: UUID, doc: MessageDoc)
}

/** Espejo en memoria: pruebas de rutas y `ENV=local` sin Firestore. */
class InMemoryChatMirror : ChatMirror {
    private val lock = Any()
    private val chatDocs = linkedMapOf<UUID, ChatDoc>()
    private val messageDocs = linkedMapOf<Pair<UUID, UUID>, MessageDoc>()

    /** Escrituras de mensajes recibidas (incluidas las repetidas). */
    var messageWrites: Int = 0
        private set

    fun chat(id: UUID): ChatDoc? = synchronized(lock) { chatDocs[id] }

    fun messages(chatId: UUID): List<MessageDoc> = synchronized(lock) { messageDocs.filterKeys { it.first == chatId }.values.toList() }

    override suspend fun upsertChat(doc: ChatDoc) = synchronized(lock) { chatDocs[doc.id] = doc }

    override suspend fun upsertMessage(chatId: UUID, doc: MessageDoc) = synchronized(lock) {
        messageWrites++
        messageDocs[chatId to doc.id] = doc
    }
}
