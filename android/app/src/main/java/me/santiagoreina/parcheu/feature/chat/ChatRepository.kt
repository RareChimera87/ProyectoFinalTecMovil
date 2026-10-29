package me.santiagoreina.parcheu.feature.chat

import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlin.time.Instant
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.tasks.await
import me.santiagoreina.parcheu.contract.ChatSummary
import me.santiagoreina.parcheu.contract.MessageDto
import me.santiagoreina.parcheu.contract.Mute
import me.santiagoreina.parcheu.contract.SendMessage
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall
import me.santiagoreina.parcheu.data.auth.FirebaseAuthSource

/** Mensajes que trae el listener en vivo y cada página hacia atrás (A-M3: nunca se lee el historial entero). */
const val CHAT_PAGE_SIZE = 50

/**
 * Chat de la persona: mensajes en tiempo real por Firestore (con la caché offline del SDK) y el resto por
 * la API. Las pruebas usan `FakeChatRepository`.
 */
interface ChatRepository {
    /** uid de Firebase de la persona con sesión; sirve para distinguir los mensajes propios. */
    val currentUid: String?

    /**
     * Los últimos [CHAT_PAGE_SIZE] mensajes del chat ordenados por `createdAt`, en vivo; falla (error en el
     * flujo) si no hay permiso o red inicial. Lo anterior se pide con [loadOlder].
     */
    fun observeMessages(chatId: String): Flow<List<RemoteMessage>>

    /**
     * Los mensajes que la moderación ocultó (`hidden == true`), en vivo y sin límite: sirve para que los
     * que ya salieron de la ventana de [observeMessages] (o vinieron de [loadOlder]) dejen de mostrar su
     * texto. Un fallo se ignora (el chat sigue con lo que tiene).
     */
    fun observeHidden(chatId: String): Flow<List<RemoteMessage>>

    /**
     * Hasta [CHAT_PAGE_SIZE] mensajes **anteriores** a [before] (excluyente), ordenados de más viejo a más
     * nuevo; una lista más corta significa que ya no hay más. Lanza [ChatLiveException] si Firestore falla.
     * Dos mensajes con el mismo `createdAt` en el borde de una página pueden omitirse (marca de servidor).
     */
    suspend fun loadOlder(chatId: String, before: Timestamp): List<RemoteMessage>

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
                messagesOf(chatId)
                    .orderBy("createdAt", Query.Direction.ASCENDING)
                    .limitToLast(CHAT_PAGE_SIZE.toLong())
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(ChatLiveException(liveErrorOf(error), error))
                        } else if (snapshot != null) {
                            trySend(snapshot.documents.map { it.toRemoteMessage() })
                        }
                    }
            awaitClose { registration.remove() }
        }
    }

    override fun observeHidden(chatId: String): Flow<List<RemoteMessage>> {
        if (FirebaseApp.getApps(authSource.applicationContext()).isEmpty()) return emptyFlow()
        return callbackFlow {
            val registration =
                messagesOf(chatId)
                    .whereEqualTo("hidden", true)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close()
                        } else if (snapshot != null) {
                            trySend(snapshot.documents.map { it.toRemoteMessage() })
                        }
                    }
            awaitClose { registration.remove() }
        }
    }

    override suspend fun loadOlder(chatId: String, before: Timestamp): List<RemoteMessage> {
        if (FirebaseApp.getApps(authSource.applicationContext()).isEmpty()) return emptyList()
        try {
            val snapshot =
                messagesOf(chatId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .startAfter(before)
                    .limit(CHAT_PAGE_SIZE.toLong())
                    .get()
                    .await()
            return snapshot.documents.map { it.toRemoteMessage() }.reversed()
        } catch (e: FirebaseFirestoreException) {
            throw ChatLiveException(liveErrorOf(e), e)
        }
    }

    private fun messagesOf(chatId: String) =
        FirebaseFirestore.getInstance().collection("chats").document(chatId).collection("messages")

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
