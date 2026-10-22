package me.santiagoreina.parcheu.feature.chat

import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    override fun observeMessages(chatId: String): Flow<List<RemoteMessage>> = messages

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
