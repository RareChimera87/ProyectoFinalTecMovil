package me.santiagoreina.parcheu.feature.chat

import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlin.time.Instant
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import me.santiagoreina.parcheu.contract.ChatSummary
import me.santiagoreina.parcheu.contract.MessageDto
import me.santiagoreina.parcheu.contract.Mute
import me.santiagoreina.parcheu.contract.SendMessage
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall
import me.santiagoreina.parcheu.data.auth.FirebaseAuthSource

/**
 * Chat de la persona: mensajes en tiempo real por Firestore (con la caché offline del SDK) y el resto por
 * la API. Las pruebas usan `FakeChatRepository`.
 */
interface ChatRepository {
    /** uid de Firebase de la persona con sesión; sirve para distinguir los mensajes propios. */
    val currentUid: String?

    /** Mensajes del chat ordenados por `createdAt`; falla (error en el flujo) si no hay permiso o red inicial. */
    fun observeMessages(chatId: String): Flow<List<RemoteMessage>>

    suspend fun chats(): ApiResult<List<ChatSummary>>

    suspend fun send(chatId: String, clientId: String, text: String): ApiResult<MessageDto>

    suspend fun markRead(chatId: String): ApiResult<Unit>

    suspend fun setMuted(chatId: String, muted: Boolean): ApiResult<Unit>
}

class FirestoreChatRepository(
    private val api: ChatApi,
    private val authSource: FirebaseAuthSource,
    private val errorMapper: ApiErrorMapper
) : ChatRepository {
    override val currentUid: String?
        get() = authSource.authOrNull()?.currentUser?.uid

    override fun observeMessages(chatId: String): Flow<List<RemoteMessage>> {
        // Sin FirebaseApp (CI sin google-services.json) no hay chat en vivo: lista vacía en lugar de fallar.
        if (FirebaseApp.getApps(authSource.applicationContext()).isEmpty()) return emptyFlow()
        return callbackFlow {
            val registration =
                FirebaseFirestore
                    .getInstance()
                    .collection("chats")
                    .document(chatId)
                    .collection("messages")
                    .orderBy("createdAt", Query.Direction.ASCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                        } else if (snapshot != null) {
                            trySend(snapshot.documents.map { it.toRemoteMessage() })
                        }
                    }
            awaitClose { registration.remove() }
        }
    }

    override suspend fun chats() = apiCall(errorMapper) { api.chats() }

    override suspend fun send(chatId: String, clientId: String, text: String) =
        apiCall(errorMapper) { api.send(chatId, SendMessage(clientId = clientId, text = text)) }

    override suspend fun markRead(chatId: String) = apiCall(errorMapper) { api.markRead(chatId) }

    override suspend fun setMuted(chatId: String, muted: Boolean) =
        apiCall(errorMapper) { api.mute(chatId, Mute(muted)) }
}

internal fun DocumentSnapshot.toRemoteMessage(): RemoteMessage = RemoteMessage(
    id = id,
    senderUid = getString("senderUid"),
    system = getBoolean("system") ?: false,
    text = getString("text").orEmpty(),
    createdAt = getTimestamp("createdAt")?.toKotlinInstant(),
    hidden = getBoolean("hidden") ?: false
)

private fun Timestamp.toKotlinInstant(): Instant = Instant.fromEpochSeconds(seconds, nanoseconds)
