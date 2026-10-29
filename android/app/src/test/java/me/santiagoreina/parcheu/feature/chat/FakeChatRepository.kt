package me.santiagoreina.parcheu.feature.chat

import com.google.firebase.Timestamp
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import me.santiagoreina.parcheu.contract.ChatSummary
import me.santiagoreina.parcheu.contract.MessageDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

/** Repositorio de chat en memoria. Los envíos esperan a [completeSend] para poder ver el estado pendiente. */
class FakeChatRepository(
    override val currentUid: String? = ME,
    var chats: ApiResult<List<ChatSummary>> = ApiResult.Success(emptyList())
) : ChatRepository {
    val messages = MutableStateFlow<List<RemoteMessage>>(emptyList())
    val sent = mutableListOf<Triple<String, String, String>>()
    var muteResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var readCalls = 0
    private var nextSend = CompletableDeferred<ApiResult<MessageDto>>()
    var autoComplete: ApiResult<MessageDto>? = null

    /** Si no es nulo, el siguiente [observeMessages] falla con este error (simula PERMISSION_DENIED, etc.). */
    var liveFailure: Throwable? = null
    var observeCalls = 0

    override fun observeMessages(chatId: String): Flow<List<RemoteMessage>> {
        observeCalls++
        val failure = liveFailure
        return if (failure != null) flow { throw failure } else messages
    }

    /** Historial completo en memoria (más viejo primero); [loadOlder] sirve páginas de [CHAT_PAGE_SIZE] a partir de él. */
    var history: List<RemoteMessage> = emptyList()
    val loadOlderCalls = mutableListOf<Timestamp>()
    var loadOlderFailure: Throwable? = null

    override suspend fun loadOlder(chatId: String, before: Timestamp): List<RemoteMessage> {
        loadOlderCalls += before
        loadOlderFailure?.let { throw it }
        return history
            .filter { (it.createdAt?.epochSeconds ?: Long.MAX_VALUE) < before.seconds }
            .takeLast(CHAT_PAGE_SIZE)
    }

    override suspend fun chats() = chats

    override suspend fun send(
        chatId: String,
        clientId: String,
        text: String
    ): ApiResult<MessageDto> {
        sent += Triple(chatId, clientId, text)
        autoComplete?.let { return it }
        return nextSend.await().also { nextSend = CompletableDeferred() }
    }

    fun completeSend(result: ApiResult<MessageDto>) {
        nextSend.complete(result)
    }

    override suspend fun markRead(chatId: String): ApiResult<Unit> {
        readCalls++
        return ApiResult.Success(Unit)
    }

    override suspend fun setMuted(chatId: String, muted: Boolean) = muteResult

    companion object {
        const val ME = "uid-me"

        fun dto(id: String, text: String) = MessageDto(
            id = id,
            senderId = "internal",
            text = text,
            createdAt = Instant.fromEpochSeconds(1_000)
        )

        val failure = ApiResult.Failure(AppError.Network)
    }
}
