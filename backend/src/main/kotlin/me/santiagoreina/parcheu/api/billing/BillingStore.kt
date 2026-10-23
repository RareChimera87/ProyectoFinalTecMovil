package me.santiagoreina.parcheu.api.billing

import kotlinx.serialization.json.JsonElement
import me.santiagoreina.parcheu.api.platform.db.tables.Entitlements
import me.santiagoreina.parcheu.api.platform.db.tables.PurchaseEvents
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.contract.EntitlementState
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** Fila de `entitlements`: el derecho vigente de un usuario (una por usuario, ADR-0013). */
data class EntitlementRow(
    val userId: UUID,
    val productId: String,
    val basePlan: String?,
    val purchaseToken: String,
    val state: EntitlementState,
    val expiresAt: Instant?,
    val autoRenewing: Boolean,
    val updatedAt: Instant,
)

/** Persistencia del dominio billing: `entitlements` y `purchase_events` (idempotencia de RTDN). */
interface BillingStore {
    fun entitlement(userId: UUID): EntitlementRow?

    /** Con [forUpdate] bloquea la fila (`select ... for update`) hasta que termine la transacción en curso. */
    fun entitlementByToken(purchaseToken: String, forUpdate: Boolean = false): EntitlementRow?

    /** Inserta o reemplaza la fila del usuario ([EntitlementRow.userId] es la llave). */
    fun save(row: EntitlementRow)

    fun userExists(userId: UUID): Boolean

    /**
     * Reclama el mensaje RTDN [messageId]: verdadero si es la primera vez que llega (se guarda con su
     * [payload]); falso si ya se había procesado. Es atómico: dos entregas simultáneas no lo reclaman las dos.
     */
    fun claimEvent(messageId: String, purchaseToken: String?, payload: JsonElement, receivedAt: Instant): Boolean

    /**
     * Ejecuta [block] en una sola transacción: las llamadas a este store dentro de [block] se unen a ella.
     * Si [block] lanza (o el proceso muere antes de confirmar) no queda nada de lo que hizo, ni siquiera el
     * [claimEvent], así que el reintento de Pub/Sub vuelve a procesar el mensaje.
     */
    fun <T> atomically(block: () -> T): T
}

class ExposedBillingStore(private val db: Database) : BillingStore {
    override fun entitlement(userId: UUID): EntitlementRow? = transaction(db) {
        Entitlements.selectAll().where { Entitlements.userId eq userId }.singleOrNull()?.toRow()
    }

    override fun entitlementByToken(purchaseToken: String, forUpdate: Boolean): EntitlementRow? = transaction(db) {
        val query = Entitlements.selectAll().where { Entitlements.purchaseToken eq purchaseToken }
        (if (forUpdate) query.forUpdate() else query).singleOrNull()?.toRow()
    }

    override fun save(row: EntitlementRow) {
        transaction(db) {
            Entitlements.upsert {
                it[userId] = row.userId
                it[productId] = row.productId
                it[basePlan] = row.basePlan
                it[purchaseToken] = row.purchaseToken
                it[state] = row.state
                it[expiresAt] = row.expiresAt?.toOffset()
                it[autoRenewing] = row.autoRenewing
                it[updatedAt] = row.updatedAt.toOffset()
            }
        }
    }

    override fun userExists(userId: UUID): Boolean = transaction(db) {
        !Users.selectAll().where { Users.id eq userId }.empty()
    }

    override fun claimEvent(messageId: String, purchaseToken: String?, payload: JsonElement, receivedAt: Instant): Boolean =
        transaction(db) {
            // `on conflict do nothing` sobre la llave `message_id`: solo la primera entrega inserta.
            PurchaseEvents.insertIgnore {
                it[PurchaseEvents.messageId] = messageId
                it[PurchaseEvents.purchaseToken] = purchaseToken
                it[PurchaseEvents.payload] = payload
                it[PurchaseEvents.receivedAt] = receivedAt.toOffset()
            }.insertedCount == 1
        }

    // Exposed reutiliza la transacción abierta en los `transaction(db)` anidados (sin nested transactions),
    // así que todo lo que el bloque hace con este store se confirma o se deshace junto.
    override fun <T> atomically(block: () -> T): T = transaction(db) { block() }

    private fun ResultRow.toRow() = EntitlementRow(
        userId = this[Entitlements.userId],
        productId = this[Entitlements.productId],
        basePlan = this[Entitlements.basePlan],
        purchaseToken = this[Entitlements.purchaseToken],
        state = this[Entitlements.state],
        expiresAt = this[Entitlements.expiresAt]?.toInstant(),
        autoRenewing = this[Entitlements.autoRenewing],
        updatedAt = this[Entitlements.updatedAt].toInstant(),
    )
}

/** Igual que [ExposedBillingStore] pero en memoria (pruebas y valor por defecto del contenedor). */
class InMemoryBillingStore(private val knownUser: (UUID) -> Boolean = { true }) : BillingStore {
    private val rows = mutableMapOf<UUID, EntitlementRow>()
    private val events = mutableMapOf<String, JsonElement>()

    /** Mensajes RTDN guardados (para los tests de idempotencia). */
    @get:Synchronized
    val eventIds: Set<String> get() = events.keys.toSet()

    @Synchronized
    override fun entitlement(userId: UUID): EntitlementRow? = rows[userId]

    @Synchronized
    override fun entitlementByToken(purchaseToken: String, forUpdate: Boolean): EntitlementRow? =
        rows.values.firstOrNull { it.purchaseToken == purchaseToken }

    @Synchronized
    override fun save(row: EntitlementRow) {
        // Mismo invariante que el `unique` de `purchase_token` en Postgres.
        check(rows.values.none { it.purchaseToken == row.purchaseToken && it.userId != row.userId }) {
            "purchase_token ya pertenece a otra fila"
        }
        rows[row.userId] = row
    }

    override fun userExists(userId: UUID): Boolean = knownUser(userId)

    @Synchronized
    override fun claimEvent(messageId: String, purchaseToken: String?, payload: JsonElement, receivedAt: Instant): Boolean {
        if (messageId in events) return false
        events[messageId] = payload
        return true
    }

    /** Copia el estado y lo restaura si [block] lanza: el mismo efecto que el rollback de Postgres. */
    @Synchronized
    override fun <T> atomically(block: () -> T): T {
        val rowsBefore = rows.toMap()
        val eventsBefore = events.toMap()
        return try {
            block()
        } catch (e: Throwable) {
            rows.clear()
            rows.putAll(rowsBefore)
            events.clear()
            events.putAll(eventsBefore)
            throw e
        }
    }
}

private fun Instant.toOffset(): OffsetDateTime = atOffset(ZoneOffset.UTC)
