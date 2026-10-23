package me.santiagoreina.parcheu.api.billing

import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.quota.DEV_GRANT_PRODUCT
import me.santiagoreina.parcheu.api.quota.DevPlusGranter
import me.santiagoreina.parcheu.api.quota.EntitlementService
import me.santiagoreina.parcheu.api.quota.grantsPlus
import me.santiagoreina.parcheu.api.quota.validateGrantDays
import me.santiagoreina.parcheu.contract.EntitlementState
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Implementación real de [EntitlementService] (E7.1): Plus sale de la fila de `entitlements` que
 * mantienen `verify` y las RTDN, con las reglas de arquitectura.md §9 ([grantsPlus]). Es la misma en
 * dev y en prod; solo fuera de prod se expone además como [DevPlusGranter] (`grant-plus`).
 */
class StoredEntitlementService(private val store: BillingStore) : EntitlementService, DevPlusGranter {
    override fun isPlus(userId: UUID, at: Instant): Boolean =
        store.entitlement(userId)?.grants(at) ?: false

    /** Concesión de dev: una fila ACTIVE con `product_id = 'dev-grant'` que vence en [days] días. */
    override fun grantPlus(userId: UUID, days: Int, now: Instant) {
        validateGrantDays(days)
        if (!store.userExists(userId)) throw NotFound("Usuario no encontrado")
        store.save(
            EntitlementRow(
                userId = userId,
                productId = DEV_GRANT_PRODUCT,
                basePlan = null,
                purchaseToken = "$DEV_GRANT_PRODUCT:$userId",
                state = EntitlementState.ACTIVE,
                expiresAt = now + Duration.ofDays(days.toLong()),
                autoRenewing = false,
                updatedAt = now,
            ),
        )
    }
}

/** ¿Esta fila da Plus en [at]? (arquitectura.md §9). */
fun EntitlementRow.grants(at: Instant): Boolean = grantsPlus(state, expiresAt, at)
