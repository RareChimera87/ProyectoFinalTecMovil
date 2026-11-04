package me.santiagoreina.parcheu.api.chat

import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.contract.ChatStatus
import me.santiagoreina.parcheu.contract.ChatType
import me.santiagoreina.parcheu.contract.Mode
import java.time.Instant
import java.util.UUID

/** Fila de `chats` con sus miembros (sin tipos de Exposed). */
data class ChatRecord(
    val id: UUID,
    val type: ChatType,
    val mode: Mode,
    val scenarioId: Int?,
    val endsAt: Instant?,
    val status: ChatStatus,
    val createdAt: Instant,
    val lastMessageAt: Instant?,
    val members: List<ChatMemberRecord>,
) {
    fun member(userId: UUID): ChatMemberRecord? = members.firstOrNull { it.userId == userId }
}

/** Fila de `chat_members` con el uid de Firebase del usuario (lo necesita el espejo de Firestore). */
data class ChatMemberRecord(
    val userId: UUID,
    val firebaseUid: String,
    val muted: Boolean,
    val lastReadAt: Instant?,
    /** Cuándo pulsó 'Llegamos a un acuerdo' en un chat SCENARIO (E4.8); nulo si aún no. */
    val agreedAt: Instant? = null,
)

/** Fila de `chat_messages`. `senderId` nulo = mensaje del sistema o autor anonimizado. */
data class MessageRecord(
    val id: UUID,
    val chatId: UUID,
    val senderId: UUID?,
    val text: String,
    val learnable: Boolean,
    val hidden: Boolean,
    val clientId: String?,
    val createdAt: Instant,
)

/** Mensaje nuevo ya validado por [ChatService]. */
data class NewMessage(
    val chatId: UUID,
    val senderId: UUID,
    val clientId: String,
    val text: String,
    val learnable: Boolean,
    val createdAt: Instant,
)

/** Resultado de guardar un mensaje: [inserted] = false si el `client_id` ya existía (reintento). */
data class SavedMessage(val message: MessageRecord, val inserted: Boolean)

/** Chat nuevo. Para SCENARIO, [scenarioId] y [endsAt] son obligatorios (CHECK de la tabla). */
data class NewChat(
    val type: ChatType,
    val mode: Mode,
    val members: List<UUID>,
    val createdAt: Instant,
    val scenarioId: Int? = null,
    val endsAt: Instant? = null,
)

/** Resultado de crear un chat: [created] = false si ya existía (chat DIRECT del par y modo). */
data class CreatedChat(val chat: ChatRecord, val created: Boolean)

/** Un renglón de `GET /v1/chats` antes de pasar por la policy de visibilidad. */
data class ChatListRow(
    val chat: ChatRecord,
    /** El otro miembro; nulo si ya no hay otro miembro (chat huérfano). */
    val other: PersonRow?,
    val lastMessage: MessageRecord?,
    /** Mensajes visibles de otros (incluido el sistema) posteriores a `last_read_at`. */
    val unread: Int,
)
