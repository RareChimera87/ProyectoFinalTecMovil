package me.santiagoreina.parcheu.core.error

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.QuotaKind

/**
 * Errores que entiende la UI (arquitectura.md §2.3). Se obtienen de un `ApiError` del contrato o de una
 * falla de red con [ApiErrorMapper]. Además de la lista de la arquitectura se agregan [TermsRequired],
 * [Forbidden] y [Conflict] para que cada `ErrorCode` del contrato tenga su caso.
 */
sealed interface AppError {
    /** Sin conexión, timeout o respuesta ilegible. */
    data object Network : AppError

    /** 401 que persiste tras refrescar el token: hay que volver a iniciar sesión. */
    data object Unauthorized : AppError

    /** 403 TERMS_REQUIRED: falta aceptar términos y política vigentes (hoja de A4). */
    data object TermsRequired : AppError

    /** 402 PLUS_REQUIRED: función de ParcheU+ (el ErrorRouter abre G1). */
    data object PlusRequired : AppError

    /** 402 QUOTA_EXCEEDED: cupo agotado (el ErrorRouter abre la G2 de [kind]). */
    data class QuotaExceeded(val kind: QuotaKind?, val resetsAt: Instant?) : AppError

    data object AgeRequired : AppError

    data object Underage : AppError

    data object Blocked : AppError

    data object Suspended : AppError

    data object Forbidden : AppError

    data object NotFound : AppError

    data object Conflict : AppError

    data object ScenarioEnded : AppError

    /** 503 AI_BUDGET: presupuesto diario de IA agotado; reintentar después de [retryAt]. */
    data class AiBudget(val retryAt: Instant?) : AppError

    /** 429 RATE_LIMITED: demasiadas peticiones seguidas a una ruta con IA; basta esperar un momento. */
    data object RateLimited : AppError

    data class Validation(val message: String) : AppError

    /** Cualquier otra cosa (5xx sin cuerpo, código desconocido). */
    data class Unknown(val httpStatus: Int? = null, val message: String? = null) : AppError
}
