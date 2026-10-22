package me.santiagoreina.parcheu.api.quota

import me.santiagoreina.parcheu.contract.QuotaKind
import java.time.Instant
import java.util.UUID

/** Resultado de intentar cobrar un uso. */
sealed interface ChargeResult {
    /** Se registró un evento nuevo en `usage_events`. */
    data object Charged : ChargeResult

    /** Ya existía un evento con ese `(kind, ref_id)`: no se cobra de nuevo. */
    data class AlreadyCharged(val ownerId: UUID, val refunded: Boolean) : ChargeResult

    /** El usuario ya gastó todo el cupo de la ventana. */
    data object LimitReached : ChargeResult
}

/**
 * Persistencia de `usage_events` (arquitectura.md §4). `ref_id` es único por `kind` en toda la
 * tabla: quien cobra un uso compartido por dos personas (p. ej. el match de E4.2) debe componer el
 * `ref_id` con el usuario (`"<matchId>:<userId>"`).
 */
interface UsageRepository {
    /**
     * Cobra un uso de forma atómica: si ya existe `(kind, refId)` devuelve [ChargeResult.AlreadyCharged];
     * si el usuario tiene `limit` o más usos no devueltos en `[window.start, window.end)` devuelve
     * [ChargeResult.LimitReached]; si no, inserta con `created_at = now`. Dos cobros simultáneos del
     * mismo usuario se serializan, así que nunca se pasa del límite.
     */
    fun charge(userId: UUID, kind: QuotaKind, refId: String, window: QuotaWindow, limit: Int, now: Instant): ChargeResult

    /** Usos no devueltos de [kind] en la ventana. */
    fun countActive(userId: UUID, kind: QuotaKind, window: QuotaWindow): Int

    /** Marca el uso como devuelto (`refunded = true`). Devuelve false si no existe ese `(kind, refId)`. */
    fun refund(kind: QuotaKind, refId: String): Boolean
}
