package me.santiagoreina.parcheu.feature.notifications

import me.santiagoreina.parcheu.contract.NotificationsDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

/** Bandeja en memoria: [inboxResults] se entregan en orden (el último se repite); registra cada `markRead`. */
class FakeNotificationsRepository(
    vararg inboxResults: ApiResult<NotificationsDto>,
    var markReadResult: ApiResult<Unit> = ApiResult.Success(Unit)
) : NotificationsRepository {
    private val queue = inboxResults.toMutableList().ifEmpty {
        mutableListOf(ApiResult.Failure(AppError.Network))
    }

    /** Cada llamada a `markRead`: la lista de ids o `null` (todas). */
    val markReadCalls = mutableListOf<List<String>?>()

    override suspend fun inbox(): ApiResult<NotificationsDto> =
        if (queue.size > 1) queue.removeAt(0) else queue.first()

    override suspend fun markRead(ids: List<String>?): ApiResult<Unit> {
        markReadCalls += ids
        return markReadResult
    }
}
