package me.santiagoreina.parcheu.api.billing

import me.santiagoreina.parcheu.api.platform.errors.Forbidden
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.api.quota.toKotlin
import me.santiagoreina.parcheu.contract.EntitlementDto
import me.santiagoreina.parcheu.contract.EntitlementState
import me.santiagoreina.parcheu.contract.VerifyPurchase
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Verificación de compras, derecho vigente y RTDN (arquitectura.md §9, ADR-0013). Play es la fuente de
 * verdad: `verify` y cada RTDN reconsultan `subscriptionsv2.get` y guardan el resultado en `entitlements`;
 * la notificación nunca se cree por sí sola. El reconocimiento (`acknowledge`) lo hace siempre el servidor.
 */
class BillingService(
    private val store: BillingStore,
    private val play: PlaySubscriptions,
    private val clock: Clock,
    private val packageName: String = DEFAULT_PACKAGE_NAME,
    private val productId: String = PLUS_PRODUCT_ID,
) {
    private val log = LoggerFactory.getLogger(BillingService::class.java)

    /** Resultado de una RTDN, para los logs y los tests. */
    enum class RtdnOutcome { PROCESSED, DUPLICATE, IGNORED }

    /** `GET /v1/billing/entitlement`: sin fila es `NONE` y gratis. */
    fun entitlement(userId: UUID): EntitlementDto = store.entitlement(userId).toDto(clock.instant())

    /**
     * `POST /v1/billing/verify`. Valida el producto y que la compra sea de esta cuenta
     * (`obfuscatedExternalAccountId = sha256(userId)`), guarda el estado y la reconoce si hace falta.
     * Una compra ajena (otro account id, o un token que ya es de otro usuario) es 403 y no toca nada.
     */
    fun verify(userId: UUID, request: VerifyPurchase): EntitlementDto {
        val token = request.purchaseToken.trim()
        if (token.isEmpty()) throw ValidationFailed("purchaseToken vacío")
        if (request.productId != productId) throw ValidationFailed("Producto desconocido: ${request.productId}")
        if (store.entitlementByToken(token)?.let { it.userId != userId } == true) {
            throw Forbidden("La compra pertenece a otra cuenta")
        }
        val sub = play.get(token) ?: throw ValidationFailed("Google Play no reconoce esta compra")
        if (sub.productId != productId) throw ValidationFailed("La compra no es de $productId")
        if (sub.obfuscatedAccountId != obfuscatedAccountId(userId)) throw Forbidden("La compra pertenece a otra cuenta")

        // La fila se relee (y se bloquea) después de consultar a Play y se guarda en la misma transacción:
        // una RTDN `SUBSCRIPTION_REVOKED` que se guardó mientras tanto no se pisa con el ACTIVE de Play.
        val state = store.atomically {
            val owner = store.entitlementByToken(token, forUpdate = true)
            if (owner != null && owner.userId != userId) throw Forbidden("La compra pertenece a otra cuenta")
            // Las RTDN que llegaron antes de verificar no tenían dueño: se ligan ahora para poder redactarlas al borrar.
            store.linkEventsOfToken(token, userId)
            sub.linkedPurchaseToken?.let { store.linkEventsOfToken(it, userId) }
            resolveState(token, sub, previous = owner, revoked = false)?.also { saveIfBetter(userId, token, sub, it, clock.instant()) }
        }
        acknowledgeIfNeeded(token, sub, state, failOnError = false)
        return entitlement(userId)
    }

    /**
     * `POST /internal/billing/rtdn`. Idempotente por `messageId` (`purchase_events`): una entrega repetida
     * no se procesa. Play se consulta **antes** de abrir la transacción (no se retiene una conexión de Hikari
     * mientras Play responde; una entrega ya reclamada ni siquiera la consulta). Dentro de ella van solo el reclamo, el `select ... for update` y el guardado
     * ([BillingStore.atomically]): si algo falla (la base, o la instancia muere a mitad) el reclamo se deshace
     * con el resto y el reintento de Pub/Sub vuelve a procesar el mensaje. Una entrega simultánea del mismo
     * mensaje espera a que la primera confirme y entonces es `DUPLICATE`.
     *
     * El `acknowledge` va después de confirmar. Si falla, se libera el reclamo y se propaga el error para
     * que Pub/Sub reintente (el reintento es idempotente: guarda el mismo estado); si ni siquiera se puede
     * liberar (la base también cayó), lo reintenta el `syncPurchases` del cliente.
     */
    fun handleRtdn(rtdn: DecodedRtdn): RtdnOutcome {
        val notification = rtdn.notification
        val subscription = notification?.subscriptionNotification
        if (store.eventClaimed(rtdn.messageId)) {
            log.info("RTDN {} repetida: se ignora", rtdn.messageId)
            return RtdnOutcome.DUPLICATE
        }
        val relevant = subscription != null && notification.packageName == packageName
        val sub = if (relevant) play.get(subscription!!.purchaseToken) else null
        val applied = store.atomically {
            if (!store.claimEvent(rtdn.messageId, subscription?.purchaseToken, rtdn.raw, clock.instant())) {
                log.info("RTDN {} repetida: se ignora", rtdn.messageId)
                Applied(RtdnOutcome.DUPLICATE)
            } else if (!relevant) {
                // Notificación de prueba, de otro tipo (compras únicas, anuladas) o de otra app: queda registrada.
                log.info("RTDN {} sin suscripción de {}: se registra y se ignora", rtdn.messageId, packageName)
                Applied(RtdnOutcome.IGNORED)
            } else {
                applyNotification(rtdn.messageId, subscription!!, sub)
            }
        }
        applied.acknowledge?.let { (token, purchase) ->
            try {
                acknowledgeIfNeeded(token, purchase, applied.state, failOnError = true)
            } catch (e: Exception) {
                runCatching { store.releaseEvent(rtdn.messageId) }
                    .onFailure { log.warn("No se pudo liberar la RTDN {}; la compra la reconocerá el syncPurchases del cliente", rtdn.messageId, it) }
                throw e
            }
        }
        return applied.outcome
    }

    /** Resultado de procesar una RTDN dentro de la transacción; [acknowledge] se ejecuta ya confirmada. */
    private class Applied(
        val outcome: RtdnOutcome,
        val state: EntitlementState? = null,
        val acknowledge: Pair<String, PlaySubscription>? = null,
    )

    private fun applyNotification(messageId: String, notification: SubscriptionNotification, sub: PlaySubscription?): Applied {
        val token = notification.purchaseToken
        val revoked = notification.notificationType == RtdnTypes.SUBSCRIPTION_REVOKED
        if (sub == null) {
            log.warn("RTDN tipo {}: Google Play no reconoce el token; se ignora", notification.notificationType)
            return Applied(RtdnOutcome.IGNORED)
        }
        if (sub.productId != productId) return Applied(RtdnOutcome.IGNORED)
        // Con bloqueo: un `verify` simultáneo espera a que esta transacción confirme (no pisa una revocación).
        val current = store.entitlementByToken(token, forUpdate = true)
        // Sin fila para el token: si viene de un cambio de plan o una nueva suscripción con el token
        // anterior, se traslada al mismo dueño (comprobando el account id); si no, la verificará la app.
        val ownerId = current?.userId ?: sub.linkedPurchaseToken
            ?.let { store.entitlementByToken(it) }
            ?.userId
            ?.takeIf { sub.obfuscatedAccountId == obfuscatedAccountId(it) }
        if (ownerId == null) {
            log.info("RTDN tipo {} de una compra aún sin verificar: la app la verificará", notification.notificationType)
            return Applied(RtdnOutcome.IGNORED)
        }
        // Privacidad (arquitectura.md §10.2): el evento de una cuenta ya borrada (lápida) no guarda el payload
        // completo ni el token; el de una viva se liga a su dueño para poder redactarlo si luego se borra.
        if (store.isTombstone(ownerId)) {
            store.redactEvent(messageId)
        } else {
            store.assignEvent(messageId, ownerId)
            // Eventos del token y del anterior (cambio de plan) que llegaron cuando aún no había dueño.
            store.linkEventsOfToken(token, ownerId)
            sub.linkedPurchaseToken?.let { store.linkEventsOfToken(it, ownerId) }
        }
        val state = resolveState(token, sub, previous = current, revoked = revoked)
        if (state != null) {
            if (current != null) {
                store.save(rowFrom(ownerId, token, sub, state, clock.instant()))
            } else {
                saveIfBetter(ownerId, token, sub, state, clock.instant())
            }
        }
        return Applied(RtdnOutcome.PROCESSED, state, token to sub)
    }

    /**
     * Estado a guardar: el de Play, salvo que la compra se haya revocado (RTDN `SUBSCRIPTION_REVOKED` ahora
     * o antes para este mismo token, aunque la fila ya sea de otra compra): un reembolso no se deshace
     * aunque Play aún muestre el periodo pagado.
     */
    private fun resolveState(token: String, sub: PlaySubscription, previous: EntitlementRow?, revoked: Boolean): EntitlementState? = when {
        revoked || previous?.state == EntitlementState.REVOKED || store.tokenRevoked(token) -> EntitlementState.REVOKED
        else -> sub.state.toEntitlementState()
    }

    /**
     * Guarda la compra como derecho del usuario sin pisar uno mejor: si ya tiene otra compra que da Plus
     * (p. ej. 'Restaurar' manda también una vieja), solo la reemplaza una que también lo dé.
     */
    private fun saveIfBetter(userId: UUID, token: String, sub: PlaySubscription, state: EntitlementState, now: Instant) {
        val candidate = rowFrom(userId, token, sub, state, now)
        val existing = store.entitlement(userId)
        val replace = existing == null || existing.purchaseToken == token || candidate.grants(now) || !existing.grants(now)
        if (replace) store.save(candidate)
    }

    private fun rowFrom(userId: UUID, token: String, sub: PlaySubscription, state: EntitlementState, now: Instant) = EntitlementRow(
        userId = userId,
        productId = sub.productId ?: productId,
        basePlan = sub.basePlan,
        purchaseToken = token,
        state = state,
        expiresAt = sub.expiresAt,
        autoRenewing = sub.autoRenewing && state != EntitlementState.REVOKED,
        updatedAt = now,
    )

    /**
     * Reconoce en el servidor una compra pagada que Play aún no tiene reconocida (si no, Play la reembolsa a
     * los 3 días). En `verify` un fallo solo se registra (no lo reintenta ninguna RTDN: esa suele llegar antes del
     * `verify` y se ignora; lo reintenta el `syncPurchases` del cliente, que vuelve a verificar las compras sin
     * reconocer, antes de que Play reembolse a los 3 días); en la RTDN se propaga para que Pub/Sub la reintente
     * (ver [handleRtdn]).
     */
    private fun acknowledgeIfNeeded(token: String, sub: PlaySubscription, state: EntitlementState?, failOnError: Boolean) {
        if (sub.acknowledged || state == null || state in NOT_ACKNOWLEDGEABLE) return
        try {
            play.acknowledge(sub.productId ?: productId, token)
        } catch (e: Exception) {
            if (failOnError) throw e
            log.warn("No se pudo reconocer la compra en Play; la reintentará el syncPurchases del cliente", e)
        }
    }

    companion object {
        const val PLUS_PRODUCT_ID = "parcheu_plus"

        /** `applicationId` de la app publicada (flavor prod); el flavor dev no está en Play. */
        const val DEFAULT_PACKAGE_NAME = "me.santiagoreina.parcheu"

        private val NOT_ACKNOWLEDGEABLE = setOf(EntitlementState.EXPIRED, EntitlementState.REVOKED)
    }
}

/** `sha256(userId)` en hex minúscula: lo que la app manda como `obfuscatedAccountId` (E7.2). */
fun obfuscatedAccountId(userId: UUID): String =
    MessageDigest.getInstance("SHA-256")
        .digest(userId.toString().toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }

/**
 * DTO del derecho. Una fila que ya venció se muestra como `EXPIRED` aunque Play aún no lo haya notificado,
 * para que `state` y `plus` nunca se contradigan.
 */
internal fun EntitlementRow?.toDto(now: Instant): EntitlementDto {
    if (this == null) return EntitlementDto(plus = false, state = EntitlementState.NONE)
    val plus = grants(now)
    val lapsed = !plus && state in TIME_BOUND_STATES
    return EntitlementDto(
        plus = plus,
        state = if (lapsed) EntitlementState.EXPIRED else state,
        basePlan = basePlan,
        expiresAt = expiresAt?.toKotlin(),
        autoRenewing = autoRenewing,
    )
}

private val TIME_BOUND_STATES = setOf(EntitlementState.ACTIVE, EntitlementState.IN_GRACE_PERIOD, EntitlementState.CANCELED)
