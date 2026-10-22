package me.santiagoreina.parcheu.api.chat

import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.contract.ChatStatus
import me.santiagoreina.parcheu.contract.Mode
import java.time.Instant
import java.util.UUID

/**
 * Acceso a datos del chat. La implementación real es [ExposedChatRepository]; [InMemoryChatRepository]
 * replica su semántica para pruebas de rutas y desarrollo local. Postgres es la fuente de verdad
 * (ADR-0004): Firestore solo es un espejo que escribe [ChatMirror].
 */
interface ChatRepository {
    /**
     * Devuelve el chat DIRECT de [a] y [b] en [mode] o lo crea. Seguro ante llamadas concurrentes
     * (dos decisiones simultáneas de E4.6 dejan un solo chat).
     */
    suspend fun getOrCreateDirect(a: UUID, b: UUID, mode: Mode, now: Instant): CreatedChat

    /** Chat DIRECT existente de [a] y [b] en [mode]; solo lectura (no crea ni reabre). */
    suspend fun findDirect(a: UUID, b: UUID, mode: Mode): ChatRecord?

    /** Crea un chat (SCENARIO, o DIRECT sin la búsqueda previa) con sus miembros. */
    suspend fun create(chat: NewChat): ChatRecord

    suspend fun chat(chatId: UUID): ChatRecord?

    /** Datos de presentación de un usuario (nombre, universidad, visibilidad, borrado). */
    suspend fun person(userId: UUID): PersonRow?

    /** Mensaje ya enviado por [senderId] con ese `client_id` (en cualquier chat). */
    suspend fun findByClientId(senderId: UUID, clientId: String): MessageRecord?

    /** Cambia el estado del chat (cerrar por bloqueo o fin del escenario). */
    suspend fun setStatus(chatId: UUID, status: ChatStatus): ChatRecord?

    /** Hay un bloqueo entre [a] y [b] en cualquier sentido. */
    suspend fun blockedBetween(a: UUID, b: UUID): Boolean

    /** Usuarios bloqueados por [userId] o que bloquearon a [userId]. */
    suspend fun blockedEitherWay(userId: UUID): Set<UUID>

    /** Consentimiento vigente (última fila de `consents`) de aprender de los chats de [mode]. */
    suspend fun learningConsent(userId: UUID, mode: Mode): Boolean

    /**
     * Guarda el mensaje y actualiza `chats.last_message_at`. Idempotente por `(sender_id, client_id)`:
     * si ya existía, no inserta nada y devuelve el guardado con `inserted = false`.
     */
    suspend fun saveMessage(message: NewMessage): SavedMessage

    /** Chats de [userId] (más recientes primero), con el otro miembro, el último mensaje y los no leídos. */
    suspend fun chatsOf(userId: UUID): List<ChatListRow>

    /** Marca leído hasta [now]. Devuelve false si [userId] no es miembro. */
    suspend fun markRead(chatId: UUID, userId: UUID, now: Instant): Boolean

    /** Silencia o reactiva los avisos del chat para [userId]. Devuelve false si no es miembro. */
    suspend fun setMuted(chatId: UUID, userId: UUID, muted: Boolean): Boolean
}
