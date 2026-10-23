package me.santiagoreina.parcheu.feature.chat

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.ChatStatus
import me.santiagoreina.parcheu.contract.ChatSummary
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.core.error.AppError

/** Largo máximo de un mensaje (arquitectura.md §5.2, E5.1: ≤ 1.000 caracteres). */
const val MAX_MESSAGE_LENGTH = 1000

/** Texto que se muestra en lugar de un mensaje oculto por moderación. */
const val HIDDEN_MESSAGE_TEXT = "Mensaje oculto"

/**
 * Mensaje tal como llega del snapshot de Firestore (`chats/{id}/messages/{msgId}`, arquitectura.md §8.2).
 * [senderUid] es el uid de Firebase del autor (`null` si [system]).
 */
data class RemoteMessage(
    val id: String,
    val senderUid: String?,
    val system: Boolean,
    val text: String,
    val createdAt: Instant?,
    val hidden: Boolean
)

/** Estado de envío de un mensaje propio. */
sealed interface MessageStatus {
    /** Ya está en el snapshot de Firestore (o la API lo confirmó). */
    data object Sent : MessageStatus

    /** Enviándose; se muestra atenuado hasta que la API responde. */
    data object Pending : MessageStatus

    /** Falló el envío: la burbuja ofrece "Reintentar" con el mismo `clientId`. */
    data class Failed(val error: AppError) : MessageStatus
}

/** Mensaje listo para dibujar. [key] es estable entre pendiente y enviado para no parpadear. */
data class ChatMessageUi(
    val key: String,
    val text: String,
    val mine: Boolean,
    val system: Boolean = false,
    val hidden: Boolean = false,
    val createdAt: Instant? = null,
    val status: MessageStatus = MessageStatus.Sent
) {
    /** Lo que ve la persona: los mensajes ocultos nunca exponen su texto. */
    val displayText: String get() = if (hidden) HIDDEN_MESSAGE_TEXT else text
}

/** Datos de la barra superior de F9. */
data class ChatHeader(
    val chatId: String,
    val name: String,
    val initials: String,
    val anonymous: Boolean,
    val otherUserId: String?,
    val mode: Mode,
    val muted: Boolean,
    val closed: Boolean,
    /** `anonId` con el que se ve a la otra persona en un chat anónimo (escenario, F3). */
    val otherAnonId: String? = null
) {
    /**
     * H1 necesita un objetivo: el uuid de la otra persona o, si es anónima, su `anonId` (el servidor
     * lo resuelve entre las personas del chat, E8.1). Nunca se manda un `anonId` como si fuera un uuid.
     */
    val canReport: Boolean get() = otherUserId != null || otherAnonId != null
}

internal fun ChatSummary.toHeader(): ChatHeader = ChatHeader(
    chatId = id,
    name = other.label,
    initials = other.initials,
    anonymous = other.id == null && other.anonId != null,
    // El anonId NO es un userId: viaja aparte (otherAnonId) y H1 lo manda como anonId.
    otherUserId = other.id,
    mode = mode,
    muted = muted,
    closed = status == ChatStatus.CLOSED,
    otherAnonId = other.anonId.takeIf { other.id == null }
)

/** Falla del listener de Firestore ya traducida a [error]; la lanza el repositorio y la lee el ViewModel. */
class ChatLiveException(val error: AppError, cause: Throwable? = null) : Exception(cause)

/** Error de la UI para una falla del listener de Firestore: sin permiso (bloqueo o salida del chat) o de red. */
internal fun liveErrorOf(e: Throwable): AppError = if (e is FirebaseFirestoreException &&
    e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
) {
    AppError.Forbidden
} else {
    AppError.Network
}
