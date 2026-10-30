package me.santiagoreina.parcheu.core.error

import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.QuotaKind
import retrofit2.HttpException

/** Traduce errores de la API y de la red a [AppError]. */
class ApiErrorMapper(
    private val json: Json = ContractJson,
    /**
     * Bus al que `apiCall` reporta cada falla (sesión, términos, Plus, cupos). `networkModule` lo resuelve
     * con `getOrNull()` desde `errorModule`; queda `null` en las pruebas que no lo necesitan.
     */
    val globalErrors: GlobalErrorBus? = null
) {

    fun map(error: ApiError): AppError = when (error.code) {
        ErrorCode.VALIDATION -> AppError.Validation(error.message)
        ErrorCode.UNAUTHENTICATED -> AppError.Unauthorized
        ErrorCode.PLUS_REQUIRED -> AppError.PlusRequired
        ErrorCode.QUOTA_EXCEEDED ->
            AppError.QuotaExceeded(
                kind = QuotaKind.entries.firstOrNull { it.name == error.kind },
                resetsAt = error.resetsAt
            )
        ErrorCode.TERMS_REQUIRED -> AppError.TermsRequired
        ErrorCode.AGE_REQUIRED -> AppError.AgeRequired
        ErrorCode.UNDERAGE -> AppError.Underage
        ErrorCode.BLOCKED -> AppError.Blocked
        ErrorCode.SUSPENDED -> AppError.Suspended
        ErrorCode.FORBIDDEN -> AppError.Forbidden
        ErrorCode.NOT_FOUND -> AppError.NotFound
        ErrorCode.CONFLICT -> AppError.Conflict
        ErrorCode.SCENARIO_ENDED -> AppError.ScenarioEnded
        ErrorCode.AI_BUDGET -> AppError.AiBudget(error.retryAt)
        ErrorCode.RATE_LIMITED -> AppError.RateLimited
    }

    /** Respuesta HTTP no exitosa: usa el `ApiError` del cuerpo y, si no se puede leer, el status. */
    fun map(httpStatus: Int, body: String?): AppError {
        val apiError =
            body
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    try {
                        json.decodeFromString(ApiError.serializer(), it)
                    } catch (_: SerializationException) {
                        null
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                }
        return apiError?.let(::map) ?: fromStatus(httpStatus)
    }

    /** Excepción de una llamada (Retrofit, OkHttp o deserialización). */
    fun map(throwable: Throwable): AppError = when (throwable) {
        is HttpException -> map(throwable.code(), throwable.response()?.errorBody()?.string())
        is IOException -> AppError.Network
        is SerializationException -> AppError.Unknown(message = throwable.message)
        else -> AppError.Unknown(message = throwable.message)
    }

    private fun fromStatus(httpStatus: Int): AppError = when (httpStatus) {
        401 -> AppError.Unauthorized
        402 -> AppError.PlusRequired
        403 -> AppError.Forbidden
        404 -> AppError.NotFound
        409 -> AppError.Conflict
        429 -> AppError.RateLimited
        else -> AppError.Unknown(httpStatus = httpStatus)
    }
}
