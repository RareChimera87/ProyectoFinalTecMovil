package me.santiagoreina.parcheu.core.network

import kotlin.coroutines.cancellation.CancellationException
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.error.GlobalErrorBus

/** Resultado de una llamada a la API ya traducido a [AppError]. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    data class Failure(val error: AppError) : ApiResult<Nothing>
}

/**
 * Ejecuta [block] (una llamada Retrofit `suspend`) y devuelve [ApiResult]; nunca lanza salvo la
 * cancelación de la corrutina. Los `XRepositoryImpl` lo usan en cada llamada.
 *
 * Es el punto único por el que pasan las fallas de la API: además de devolverla, publica la falla en el
 * [GlobalErrorBus] del mapper (sesión vencida, suspensión, términos, Plus y cupos; arquitectura.md §2.3).
 * [routesPremium] `false` es para llamadas cuyo 402 la pantalla muestra como estado bloqueado (D9) y no
 * debe abrir G1.
 */
suspend fun <T> apiCall(
    mapper: ApiErrorMapper,
    routesPremium: Boolean = true,
    block: suspend () -> T
): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    val error = mapper.map(e)
    mapper.globalErrors?.publish(error, routesPremium)
    ApiResult.Failure(error)
}
