package me.santiagoreina.parcheu.api.billing

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.quota.DEV_GRANT_PRODUCT
import me.santiagoreina.parcheu.contract.EntitlementState
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Activación manual de ParcheU+ (enmienda de ADR-0013): la ejecuta solo quien puede correr el Cloud Run Job
 * `grant-plus` del proyecto (IAM de GCP), nunca una ruta HTTP. La fila resultante de `entitlements` es una más
 * para [StoredEntitlementService], así que cuotas y gating la tratan igual que una compra de Play.
 *
 * - Origen explícito: `product_id = 'manual-grant'` y `purchase_token = 'manual-grant:<uuid>'` (el uuid lo
 *   reescribe la purga de E8.3 igual que el de `dev-grant`). Ninguna respuesta de Play ni RTDN usa ese token,
 *   así que `verify` y las RTDN no lo tocan; una compra real que da Plus sí lo reemplaza (`saveIfBetter`).
 * - Vencimiento obligatorio: 1..[MAX_MANUAL_DAYS] días, nunca indefinido. Repetir extiende o deja igual
 *   (el vencimiento nunca baja ni pasa de hoy + [MAX_MANUAL_DAYS]); hay una sola fila por usuario.
 * - Cada ejecución deja un evento en `purchase_events` (`message_id = 'manual-grant:<uuid aleatorio>'`, ligado
 *   al usuario, sin correo ni datos de pago) y un log estructurado con el uuid interno, nunca el correo.
 */
class ManualPlusService(
    private val store: BillingStore,
    private val users: ManualPlusUsers,
    private val clock: Clock,
    private val newEventId: () -> UUID = UUID::randomUUID,
) {
    private val log = LoggerFactory.getLogger(ManualPlusService::class.java)

    fun grant(selector: UserSelector, days: Int): ManualPlusOutcome.Granted {
        require(days in 1..MAX_MANUAL_DAYS) { "--days debe estar entre 1 y $MAX_MANUAL_DAYS (0 revoca)" }
        val now = clock.instant()
        val user = eligible(selector, now)
        val outcome = store.atomically {
            val current = store.entitlement(user.id)
            if (current != null && !current.isManual() && current.isLivePurchase(now)) {
                refuse(ManualPlusRefused.Reason.PLAY_ACTIVE, user.id, "El usuario ya tiene una suscripción de Play vigente: no se toca")
            }
            val target = now + Duration.ofDays(days.toLong())
            val previous = current?.takeIf { it.isManual() && it.grants(now) }?.expiresAt
            val expiresAt = if (previous != null && previous.isAfter(target)) previous else target
            val changed = previous != expiresAt
            if (changed) {
                store.save(
                    EntitlementRow(
                        userId = user.id,
                        productId = MANUAL_GRANT_PRODUCT,
                        basePlan = null,
                        purchaseToken = manualToken(user.id),
                        state = EntitlementState.ACTIVE,
                        expiresAt = expiresAt,
                        autoRenewing = false,
                        updatedAt = now,
                    ),
                )
            }
            audit(user.id, "GRANT", now, days, expiresAt)
            ManualPlusOutcome.Granted(user.id, days, expiresAt, extended = previous != null, changed = changed)
        }
        log.atInfo().setMessage(GRANTED_EVENT).addKeyValue("event", GRANTED_EVENT)
            .addKeyValue("userId", outcome.userId.toString()).addKeyValue("days", days)
            .addKeyValue("expiresAt", outcome.expiresAt.toString()).addKeyValue("changed", outcome.changed).log()
        return outcome
    }

    /** Retira el Plus manual: la fila queda `EXPIRED` con vencimiento ahora. Sin Plus manual vigente no hace nada. */
    fun revoke(selector: UserSelector): ManualPlusOutcome {
        val now = clock.instant()
        val user = find(selector, now)
        val outcome = store.atomically {
            val current = store.entitlement(user.id)
            when {
                current == null || (current.isManual() && !current.grants(now)) -> ManualPlusOutcome.NothingToRevoke(user.id)
                !current.isManual() -> refuse(ManualPlusRefused.Reason.NOT_MANUAL, user.id, "El derecho del usuario no es manual (Play o dev): no se toca")
                else -> {
                    store.save(current.copy(state = EntitlementState.EXPIRED, expiresAt = now, autoRenewing = false, updatedAt = now))
                    audit(user.id, "REVOKE", now, days = 0, expiresAt = now)
                    ManualPlusOutcome.Revoked(user.id)
                }
            }
        }
        log.atInfo().setMessage(REVOKED_EVENT).addKeyValue("event", REVOKED_EVENT)
            .addKeyValue("userId", outcome.userId.toString()).addKeyValue("revoked", outcome is ManualPlusOutcome.Revoked).log()
        return outcome
    }

    /** Usuario único, vivo, no suspendido y no demo (solo para conceder: revocar acepta suspendidos). */
    private fun eligible(selector: UserSelector, now: Instant): ManualPlusUser {
        val user = find(selector, now)
        when {
            user.isDemo -> refuse(ManualPlusRefused.Reason.DEMO, user.id, "Es un usuario demo (ADR-0027): no recibe Plus")
            user.suspendedUntil?.isAfter(now) == true -> refuse(ManualPlusRefused.Reason.SUSPENDED, user.id, "El usuario está suspendido")
        }
        return user
    }

    private fun find(selector: UserSelector, now: Instant): ManualPlusUser {
        val matches = users.find(selector)
        val user = when (matches.size) {
            0 -> refuse(ManualPlusRefused.Reason.NOT_FOUND, null, "No existe un usuario con ese ${selector.label}")
            1 -> matches.single()
            else -> refuse(ManualPlusRefused.Reason.AMBIGUOUS, null, "Hay ${matches.size} usuarios con ese correo: usa --uid")
        }
        if (user.deleted) refuse(ManualPlusRefused.Reason.DELETED, user.id, "La cuenta está borrada")
        return user
    }

    private fun audit(userId: UUID, action: String, now: Instant, days: Int, expiresAt: Instant) {
        val messageId = "$MANUAL_GRANT_PRODUCT:${newEventId()}"
        val payload = buildJsonObject {
            put("source", MANUAL_SOURCE)
            put("action", action)
            put("days", days)
            put("expiresAt", expiresAt.toString())
            put("eventTimeMillis", now.toEpochMilli().toString())
        }
        check(store.claimEvent(messageId, manualToken(userId), payload, now)) { "Evento de auditoría repetido: $messageId" }
        store.assignEvent(messageId, userId)
    }

    private fun refuse(reason: ManualPlusRefused.Reason, userId: UUID?, message: String): Nothing {
        log.atWarn().setMessage(REFUSED_EVENT).addKeyValue("event", REFUSED_EVENT)
            .addKeyValue("reason", reason.name).addKeyValue("userId", userId?.toString()).log()
        throw ManualPlusRefused(reason, message)
    }

    companion object {
        const val MAX_MANUAL_DAYS = 30
        const val DEFAULT_MANUAL_DAYS = 7
        const val GRANTED_EVENT = "plus_granted_manual"
        const val REVOKED_EVENT = "plus_revoked_manual"
        const val REFUSED_EVENT = "plus_manual_refused"
    }
}

/** `product_id` de las filas manuales: el origen explícito MANUAL de la fila de `entitlements`. */
const val MANUAL_GRANT_PRODUCT = "manual-grant"

/** `source` de los eventos de auditoría de `purchase_events`. */
const val MANUAL_SOURCE = "MANUAL"

internal fun manualToken(userId: UUID) = "$MANUAL_GRANT_PRODUCT:$userId"

fun EntitlementRow.isManual(): Boolean = productId == MANUAL_GRANT_PRODUCT

/** Estados de Play de los que aún puede volver Plus sin comprar de nuevo (suspensión, pausa, gracia). */
private val RECOVERABLE_STATES = setOf(EntitlementState.ACTIVE, EntitlementState.IN_GRACE_PERIOD, EntitlementState.ON_HOLD, EntitlementState.PAUSED)

/**
 * ¿Es una compra que no se debe pisar? Da Plus ahora o puede volver a darlo sola (ON_HOLD, PAUSED): pisarla
 * desligaría el token de Play y las RTDN de recuperación ya no encontrarían la fila. Una de `dev-grant` no cuenta.
 */
private fun EntitlementRow.isLivePurchase(now: Instant): Boolean =
    productId != DEV_GRANT_PRODUCT && (grants(now) || state in RECOVERABLE_STATES)

/** A quién se le concede: por correo (igualdad exacta sin distinguir mayúsculas, sin comodines) o por uid de Firebase. */
sealed interface UserSelector {
    val label: String

    data class ByEmail(val email: String) : UserSelector {
        override val label get() = "correo"
    }

    data class ByFirebaseUid(val uid: String) : UserSelector {
        override val label get() = "uid"
    }
}

data class ManualPlusUser(val id: UUID, val isDemo: Boolean, val deleted: Boolean, val suspendedUntil: Instant?)

fun interface ManualPlusUsers {
    fun find(selector: UserSelector): List<ManualPlusUser>
}

class ExposedManualPlusUsers(private val db: Database) : ManualPlusUsers {
    override fun find(selector: UserSelector): List<ManualPlusUser> = transaction(db) {
        val query = when (selector) {
            is UserSelector.ByEmail -> Users.selectAll().where { Users.email.lowerCase() eq selector.email.lowercase() }
            is UserSelector.ByFirebaseUid -> Users.selectAll().where { Users.firebaseUid eq selector.uid }
        }
        query.map {
            ManualPlusUser(
                id = it[Users.id],
                isDemo = it[Users.isDemo],
                deleted = it[Users.deletedAt] != null || it[Users.firebaseUid].startsWith("deleted:"),
                suspendedUntil = it[Users.suspendedUntil]?.toInstant(),
            )
        }
    }
}

sealed interface ManualPlusOutcome {
    val userId: UUID

    /** [changed] falso: ya tenía un Plus manual que vence más tarde y se dejó igual. */
    data class Granted(override val userId: UUID, val days: Int, val expiresAt: Instant, val extended: Boolean, val changed: Boolean) : ManualPlusOutcome

    data class Revoked(override val userId: UUID) : ManualPlusOutcome

    data class NothingToRevoke(override val userId: UUID) : ManualPlusOutcome
}

/** Rechazo esperado (usuario inexistente, demo, borrado, suspendido o con Play vigente): el job sale con código 3. */
class ManualPlusRefused(val reason: Reason, message: String) : RuntimeException(message) {
    enum class Reason { NOT_FOUND, AMBIGUOUS, DELETED, SUSPENDED, DEMO, PLAY_ACTIVE, NOT_MANUAL }
}
