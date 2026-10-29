package me.santiagoreina.parcheu.core.error

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.santiagoreina.parcheu.contract.QuotaKind

/**
 * Errores que no son de una pantalla sino de toda la app (arquitectura.md §2.3): los publica `apiCall` y
 * los consume `GlobalErrorHandler` en `ParcheUApp`, que navega a A4, G1 o G2.
 */
sealed interface GlobalError {
    /** 401 que persiste (sesión vencida o cuenta borrada en otro dispositivo): se cierra sesión. */
    data object SessionExpired : GlobalError

    /**
     * 403 SUSPENDED: la cuenta está suspendida o baneada. La sesión se conserva y se abre `Suspended`, desde
     * donde la persona puede exportar sus datos, borrar la cuenta o cerrar sesión (rutas exentas en el
     * backend). Cerrar sesión no serviría: `POST /v1/session` no revisa la suspensión y volvería a entrar.
     */
    data object AccountSuspended : GlobalError

    /** 403 TERMS_REQUIRED: la hoja de A4 pide aceptar la versión vigente (la sesión se conserva). */
    data object TermsRequired : GlobalError

    /** 402 PLUS_REQUIRED: abre G1. */
    data object PlusRequired : GlobalError

    /** 402 QUOTA_EXCEEDED: abre la G2 de [kind] (G1 si el servidor no dijo cuál). */
    data class QuotaExceeded(val kind: QuotaKind?, val resetsAt: Instant?) : GlobalError

    companion object {
        /** Caso global de [error], o `null` si lo resuelve solo la pantalla que hizo la llamada. */
        fun of(error: AppError): GlobalError? = when (error) {
            AppError.Unauthorized -> SessionExpired
            AppError.Suspended -> AccountSuspended
            AppError.TermsRequired -> TermsRequired
            AppError.PlusRequired -> PlusRequired
            is AppError.QuotaExceeded -> QuotaExceeded(error.kind, error.resetsAt)
            else -> null
        }
    }
}

/**
 * Bus de [GlobalError] (Koin single, `errorModule`). [publish] lo llama `apiCall` con cada falla, además
 * de devolverla a quien llamó, que sigue pudiendo mostrar su propio estado.
 *
 * - Sin sesión ([hasSession] `false`) un 401 no se publica: es lo esperado tras cerrar sesión o antes de
 *   entrar, y Splash y A4 lo tratan ellos mismos.
 * - El mismo error repetido dentro de [dedupeWindow] se publica una sola vez: al vencer la sesión fallan
 *   a la vez todas las llamadas en curso y solo se debe navegar una vez.
 * - Sin nadie escuchando (app en segundo plano) los eventos se pierden: no hay `replay`, para no navegar
 *   al volver por un error viejo.
 */
class GlobalErrorBus(
    private val hasSession: () -> Boolean,
    private val dedupeWindow: Duration = DEDUPE_WINDOW,
    private val timeSource: TimeSource = TimeSource.Monotonic
) {
    private val _errors =
        MutableSharedFlow<GlobalError>(
            extraBufferCapacity = BUFFER,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    val errors: SharedFlow<GlobalError> = _errors.asSharedFlow()

    private var last: Pair<GlobalError, TimeMark>? = null

    /**
     * Publica el caso global de [error], si lo tiene. Con [routesPremium] `false` los 402 no se publican:
     * la pantalla los muestra como un estado bloqueado propio (p. ej. D9). Devuelve `true` si publicó.
     */
    fun publish(error: AppError, routesPremium: Boolean = true): Boolean {
        val global = GlobalError.of(error) ?: return false
        if (!routesPremium && global.isPremium()) return false
        if (global == GlobalError.SessionExpired && !hasSession()) return false
        synchronized(this) {
            val previous = last
            if (previous != null && previous.first == global &&
                previous.second.elapsedNow() < dedupeWindow
            ) {
                return false
            }
            last = global to timeSource.markNow()
        }
        return _errors.tryEmit(global)
    }

    private fun GlobalError.isPremium(): Boolean =
        this == GlobalError.PlusRequired || this is GlobalError.QuotaExceeded

    companion object {
        val DEDUPE_WINDOW: Duration = 3.seconds
        private const val BUFFER = 16
    }
}
