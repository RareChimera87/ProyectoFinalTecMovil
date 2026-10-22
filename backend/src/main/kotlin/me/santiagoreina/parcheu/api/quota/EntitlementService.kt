package me.santiagoreina.parcheu.api.quota

import me.santiagoreina.parcheu.api.platform.db.tables.Entitlements
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.contract.EntitlementState
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * ¿El usuario tiene ParcheU+ en este instante? La implementación real (Play Billing, RTDN) llega
 * con E7.1 y reemplaza el cableado de `AppContainer`; las cuotas solo dependen de esta interfaz.
 */
fun interface EntitlementService {
    fun isPlus(userId: UUID, at: Instant): Boolean
}

/** Stub 'gratis' hasta E7.1: nadie es Plus. Es el que se cablea en prod. */
object FreeEntitlementService : EntitlementService {
    override fun isPlus(userId: UUID, at: Instant): Boolean = false
}

/** Concede Plus por unos días sin pasar por Play (`POST /internal/dev/grant-plus`, nunca en prod). */
interface DevPlusGranter {
    fun grantPlus(userId: UUID, days: Int, now: Instant)
}

/** Estados de `entitlements` que dan Plus siempre (arquitectura.md §9: periodo de gracia incluido). */
internal val PLUS_STATES = setOf(EntitlementState.ACTIVE, EntitlementState.IN_GRACE_PERIOD)

/**
 * ¿Una fila de `entitlements` da Plus en [at]? ACTIVE e IN_GRACE_PERIOD mientras no hayan vencido
 * (sin `expires_at` cuenta); CANCELED solo antes de `expires_at` (periodo ya pagado).
 */
internal fun grantsPlus(state: EntitlementState, expiresAt: Instant?, at: Instant): Boolean = when (state) {
    in PLUS_STATES -> expiresAt?.isAfter(at) ?: true
    EntitlementState.CANCELED -> expiresAt?.isAfter(at) ?: false
    else -> false
}

internal const val DEV_GRANT_PRODUCT = "dev-grant"
internal const val MAX_GRANT_DAYS = 365

internal fun validateGrantDays(days: Int) {
    if (days !in 1..MAX_GRANT_DAYS) throw ValidationFailed("days debe estar entre 1 y $MAX_GRANT_DAYS")
}

/**
 * Entorno dev: lee `entitlements` (fila ACTIVE o IN_GRACE_PERIOD sin vencer) y permite concederla
 * con `grant-plus`. La concesión es una fila con `product_id = 'dev-grant'`.
 */
class ExposedDevEntitlementService(private val db: Database) : EntitlementService, DevPlusGranter {
    override fun isPlus(userId: UUID, at: Instant): Boolean = transaction(db) {
        Entitlements.selectAll().where { Entitlements.userId eq userId }.singleOrNull()?.let { row ->
            grantsPlus(row[Entitlements.state], row[Entitlements.expiresAt]?.toInstant(), at)
        } ?: false
    }

    override fun grantPlus(userId: UUID, days: Int, now: Instant) {
        validateGrantDays(days)
        transaction(db) {
            if (Users.selectAll().where { Users.id eq userId }.empty()) throw NotFound("Usuario no encontrado")
            Entitlements.upsert {
                it[Entitlements.userId] = userId
                it[productId] = DEV_GRANT_PRODUCT
                it[basePlan] = null
                it[purchaseToken] = "$DEV_GRANT_PRODUCT:$userId"
                it[state] = EntitlementState.ACTIVE
                it[expiresAt] = (now + Duration.ofDays(days.toLong())).toOffset()
                it[autoRenewing] = false
                it[updatedAt] = now.toOffset()
            }
        }
    }
}

/** Igual que [ExposedDevEntitlementService] pero en memoria (pruebas y `ENV=local` sin base). */
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
