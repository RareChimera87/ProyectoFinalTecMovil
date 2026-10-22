package me.santiagoreina.parcheu.contract

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/* Chats directos y de escenario (`/v1/chats/...`); el tiempo real va por Firestore (arquitectura.md §8). */

@Serializable
public enum class ChatType { DIRECT, SCENARIO }

@Serializable
public enum class ChatStatus { OPEN, CLOSED }

/**
 * Mensaje. `senderId` es el uuid interno (nulo = mensaje del sistema); en Firestore el campo
 * equivalente es `senderUid` con el uid de Firebase.
 */
@Serializable
public data class MessageDto(
    val id: String,
    val senderId: String? = null,
    val text: String,
    val createdAt: Instant,
    val hidden: Boolean = false,
)

/** GET /v1/chats (lista). */
@Serializable
public data class ChatSummary(
    val id: String,
    val type: ChatType,
    val mode: Mode,
    val other: PersonRef,
    val lastMessage: MessageDto? = null,
    val unread: Int = 0,
    val muted: Boolean = false,
    val endsAt: Instant? = null,
    val status: ChatStatus = ChatStatus.OPEN,
)

/** POST /v1/chats/{id}/messages; `clientId` hace idempotente el envío. */
@Serializable
public data class SendMessage(
    val clientId: String,
    val text: String,
)

/** POST /v1/chats/{id}/mute */
@Serializable
public data class Mute(
    val muted: Boolean,
)
