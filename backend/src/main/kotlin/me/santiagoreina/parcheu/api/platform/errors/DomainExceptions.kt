package me.santiagoreina.parcheu.api.platform.errors

import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.QuotaKind
import kotlin.time.Instant

/**
 * Excepción de dominio: los servicios la lanzan y `StatusPages` la traduce a [ApiError] con el
 * status HTTP de su [code] (arquitectura.md §5.1). Nunca se captura en rutas.
 */
open class DomainException(
    val code: ErrorCode,
    message: String,
    val kind: String? = null,
    val resetsAt: Instant? = null,
    val retryAt: Instant? = null,
) : RuntimeException(message) {
    fun toApiError() = ApiError(code, message ?: code.name, kind, resetsAt, retryAt)
}

class ValidationFailed(message: String) : DomainException(ErrorCode.VALIDATION, message)
class Unauthenticated(message: String = "Falta un token válido") : DomainException(ErrorCode.UNAUTHENTICATED, message)
class PlusRequired(message: String = "Esta función es de ParcheU+") : DomainException(ErrorCode.PLUS_REQUIRED, message)

/** Cupo agotado: 402 con `kind` (si se conoce) y `resetsAt`. */
class QuotaExceeded(resetsAt: Instant, kind: QuotaKind? = null) :
    DomainException(ErrorCode.QUOTA_EXCEEDED, "Cupo agotado", kind?.name, resetsAt = resetsAt)

class TermsRequired(message: String = "Debes aceptar los términos vigentes") : DomainException(ErrorCode.TERMS_REQUIRED, message)
class AgeRequired(message: String = "Se requiere verificar la edad") : DomainException(ErrorCode.AGE_REQUIRED, message)
class Underage(message: String = "ParcheU es solo para mayores de 18 años") : DomainException(ErrorCode.UNDERAGE, message)
class Blocked(message: String = "Hay un bloqueo entre las partes") : DomainException(ErrorCode.BLOCKED, message)
class Suspended(message: String = "Usuario suspendido") : DomainException(ErrorCode.SUSPENDED, message)
class Forbidden(message: String = "Sin permiso") : DomainException(ErrorCode.FORBIDDEN, message)
class NotFound(message: String = "No existe o no es visible") : DomainException(ErrorCode.NOT_FOUND, message)
class Conflict(message: String) : DomainException(ErrorCode.CONFLICT, message)
class ScenarioEnded(message: String = "El escenario ya terminó") : DomainException(ErrorCode.SCENARIO_ENDED, message)

/** Presupuesto diario de IA agotado: 503 con `retryAt`. */
class AiBudgetExceeded(retryAt: Instant) :
    DomainException(ErrorCode.AI_BUDGET, "Presupuesto diario de IA agotado", retryAt = retryAt)
