package me.santiagoreina.parcheu.core.network

import kotlin.coroutines.cancellation.CancellationException
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.error.AppError

/** Resultado de una llamada a la API ya traducido a [AppError]. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    data class Failure(val error: AppError) : ApiResult<Nothing>
}

/**
 * Ejecuta [block] (una llamada Retrofit `suspend`) y devuelve [ApiResult]; nunca lanza salvo la
 * cancelación de la corrutina. Los `XRepositoryImpl` lo usan en cada llamada.
 */
suspend fun <T> apiCall(mapper: ApiErrorMapper, block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    ApiResult.Failure(mapper.map(e))
}
