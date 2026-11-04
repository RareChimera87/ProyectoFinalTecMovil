package me.santiagoreina.parcheu.data.account

import me.santiagoreina.parcheu.core.network.ApiResult

/** [AccountRepository] en memoria. [calls] registra "delete" y "export" en orden. */
class FakeAccountRepository(
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit),
    var exportResult: ApiResult<String> = ApiResult.Success(SAMPLE_EXPORT)
) : AccountRepository {
    val calls = mutableListOf<String>()

    override suspend fun deleteAccount(): ApiResult<Unit> {
        calls += "delete"
        return deleteResult
    }

    override suspend fun exportJson(): ApiResult<String> {
        calls += "export"
        return exportResult
    }

    companion object {
        const val SAMPLE_EXPORT = """{"generatedAt":"2026-10-25T12:00:00Z","me":{"id":"u-ana"}}"""
    }
}
