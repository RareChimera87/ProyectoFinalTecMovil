package me.santiagoreina.parcheu.feature.home

import me.santiagoreina.parcheu.contract.HomeDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

/** `GET /v1/home` en memoria: [results] se entregan en orden (el último se repite). */
class FakeHomeRepository(vararg results: ApiResult<HomeDto>) : HomeRepository {
    private val queue = results.toMutableList().ifEmpty {
        mutableListOf(ApiResult.Failure(AppError.Network))
    }
    var calls = 0
        private set

    override suspend fun home(): ApiResult<HomeDto> {
        calls++
        return if (queue.size > 1) queue.removeAt(0) else queue.first()
    }
}
