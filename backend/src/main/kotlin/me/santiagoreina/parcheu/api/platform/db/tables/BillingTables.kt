package me.santiagoreina.parcheu.api.platform.db.tables

import me.santiagoreina.parcheu.contract.EntitlementState
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/* Billing (ADR-0013): estado de la suscripción y eventos RTDN de Play. */

object Entitlements : Table("entitlements") {
    val userId = userRef("user_id")
    val productId = text("product_id")
    val basePlan = text("base_plan").nullable()
    val purchaseToken = text("purchase_token")
    /** `NONE` del contrato nunca se guarda: es la ausencia de fila. */
    val state = enumText<EntitlementState>("state")
    val expiresAt = timestampWithTimeZone("expires_at").nullable()
    val autoRenewing = bool("auto_renewing").default(false)
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

/** Idempotencia de RTDN por id del mensaje de Pub/Sub. */
object PurchaseEvents : Table("purchase_events") {
    val messageId = text("message_id")
    val purchaseToken = text("purchase_token").nullable()
    /** Cuenta dueña de la compra cuando se conoce; sin llave foránea: la purga la pone en `null` (E8.3). */
    val userId = javaUUID("user_id").nullable()
    val payload = jsonbColumn("payload")
    val receivedAt = timestampWithTimeZone("received_at")
    override val primaryKey = PrimaryKey(messageId)
}
