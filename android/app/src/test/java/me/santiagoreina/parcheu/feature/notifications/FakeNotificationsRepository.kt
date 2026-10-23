package me.santiagoreina.parcheu.feature.notifications

import kotlinx.coroutines.CompletableDeferred
import me.santiagoreina.parcheu.contract.NotificationsDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

/**
 * Bandeja en memoria: [inboxResults] se entregan en orden (el último se repite); registra cada `markRead`.
 * Con [markAllGate], `markRead(null)` espera a que se complete (para probar carreras con 'Marcar leídas').
 */
class FakeNotificationsRepository(
    vararg inboxResults: ApiResult<NotificationsDto>,
    var markReadResult: ApiResult<Unit> = ApiResult.Success(Unit),
    var markAllGate: CompletableDeferred<Unit>? = null
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
        if (ids == null) markAllGate?.await()
        return if (ids == null) markReadResult else ApiResult.Success(Unit)
    }
}
