package me.santiagoreina.parcheu.contract

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/* ParcheU+ (arquitectura.md §9): derecho vigente, verificación de compras y concesión en dev. */

/** Estados de Play más NONE (nunca compró). `plus` se deriva en el servidor según §9. */
@Serializable
public enum class EntitlementState { NONE, ACTIVE, IN_GRACE_PERIOD, ON_HOLD, PAUSED, CANCELED, EXPIRED, REVOKED }

/** GET /v1/billing/entitlement y respuesta de verify. `basePlan`: `mensual` o `anual`. */
@Serializable
public data class EntitlementDto(
    val plus: Boolean,
    val state: EntitlementState,
    val basePlan: String? = null,
    val expiresAt: Instant? = null,
    val autoRenewing: Boolean? = null,
)

/** POST /v1/billing/verify */
@Serializable
public data class VerifyPurchase(
    val purchaseToken: String,
    val productId: String,
)

/** POST /internal/dev/grant-plus (solo dev). */
@Serializable
public data class GrantPlus(
    val userId: String,
    val days: Int,
)
