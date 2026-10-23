package me.santiagoreina.parcheu.api.quota

import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.contract.EntitlementState
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * ¿El usuario tiene ParcheU+ en este instante? La implementación real es `billing.StoredEntitlementService`
 * (E7.1: tabla `entitlements` que mantienen `verify` y las RTDN); las cuotas solo dependen de esta interfaz.
 */
fun interface EntitlementService {
    fun isPlus(userId: UUID, at: Instant): Boolean
}

/** Concede Plus por unos días sin pasar por Play (`POST /internal/dev/grant-plus`, nunca en prod). */
interface DevPlusGranter {
    fun grantPlus(userId: UUID, days: Int, now: Instant)
}

/** Estados de `entitlements` que dan Plus siempre (arquitectura.md §9: periodo de gracia incluido). */
internal val PLUS_STATES = setOf(EntitlementState.ACTIVE, EntitlementState.IN_GRACE_PERIOD)

/**
 * Margen tras `expires_at` de una suscripción ACTIVE que se renueva sola: el cobro puede tardar minutos
 * y la RTDN `SUBSCRIPTION_RENEWED` llega después, así que no se corta Plus en el segundo exacto.
 */
internal val RENEWAL_MARGIN: Duration = Duration.ofHours(1)

/**
 * ¿Una fila de `entitlements` da Plus en [at]? ACTIVE e IN_GRACE_PERIOD mientras no hayan vencido
 * (sin `expires_at` cuenta); CANCELED solo antes de `expires_at` (periodo ya pagado). Solo ACTIVE con
 * [autoRenewing] tiene además [RENEWAL_MARGIN] de margen; ninguno más (arquitectura.md §9).
 */
internal fun grantsPlus(state: EntitlementState, expiresAt: Instant?, at: Instant, autoRenewing: Boolean = false): Boolean = when {
    state == EntitlementState.ACTIVE && autoRenewing -> expiresAt?.plus(RENEWAL_MARGIN)?.isAfter(at) ?: true
    state in PLUS_STATES -> expiresAt?.isAfter(at) ?: true
    state == EntitlementState.CANCELED -> expiresAt?.isAfter(at) ?: false
    else -> false
}

internal const val DEV_GRANT_PRODUCT = "dev-grant"
internal const val MAX_GRANT_DAYS = 365

internal fun validateGrantDays(days: Int) {
    if (days !in 1..MAX_GRANT_DAYS) throw ValidationFailed("days debe estar entre 1 y $MAX_GRANT_DAYS")
}

/** Concesión de dev en memoria, sin la tabla `entitlements` (pruebas de otros dominios y `ENV=local` sin base). */
class InMemoryDevEntitlementService(private val knownUser: (UUID) -> Boolean = { true }) : EntitlementService, DevPlusGranter {
    private val plusUntil = mutableMapOf<UUID, Instant>()

    @Synchronized
    override fun isPlus(userId: UUID, at: Instant): Boolean = plusUntil[userId]?.isAfter(at) ?: false

    @Synchronized
    override fun grantPlus(userId: UUID, days: Int, now: Instant) {
        validateGrantDays(days)
        if (!knownUser(userId)) throw NotFound("Usuario no encontrado")
        plusUntil[userId] = now + Duration.ofDays(days.toLong())
    }
}
