package me.santiagoreina.parcheu.data.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall

/** Borrar la cuenta y descargar mis datos (E8.3). Las pruebas usan `FakeAccountRepository`. */
interface AccountRepository {
    /** `DELETE /v1/me`. Tras el éxito la sesión local debe cerrarse: la cuenta ya no responde. */
    suspend fun deleteAccount(): ApiResult<Unit>

    /** `GET /v1/me/export`: el JSON completo de la persona, como texto. */
    suspend fun exportJson(): ApiResult<String>
}

class AccountRepositoryImpl(private val api: AccountApi, private val errorMapper: ApiErrorMapper) :
    AccountRepository {
    override suspend fun deleteAccount(): ApiResult<Unit> = apiCall(errorMapper) { api.deleteMe() }

    override suspend fun exportJson(): ApiResult<String> = apiCall(errorMapper) {
        withContext(Dispatchers.IO) { api.export().use { it.string() } }
    }
}
