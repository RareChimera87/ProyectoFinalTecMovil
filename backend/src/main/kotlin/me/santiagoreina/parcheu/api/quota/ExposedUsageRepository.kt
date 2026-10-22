package me.santiagoreina.parcheu.api.quota

import me.santiagoreina.parcheu.api.platform.db.tables.UsageEvents
import me.santiagoreina.parcheu.api.platform.db.tables.Users
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.contract.QuotaKind
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * `usage_events` en Postgres. Cada cobro bloquea la fila del usuario (`SELECT ... FOR UPDATE`)
 * antes de contar, así que los cobros simultáneos del mismo usuario se serializan y nunca se pasa
 * del límite. La unicidad de `(kind, ref_id)` la garantiza la base (`ON CONFLICT DO NOTHING`).
 */
class ExposedUsageRepository(private val db: Database) : UsageRepository {
    override fun charge(userId: UUID, kind: QuotaKind, refId: String, window: QuotaWindow, limit: Int, now: Instant): ChargeResult =
        transaction(db) {
            val locked = Users.select(Users.id).where { Users.id eq userId }.forUpdate().any()
            if (!locked) throw NotFound("Usuario no encontrado")
            existing(kind, refId)?.let { return@transaction it }
            if (active(userId, kind, window) >= limit) return@transaction ChargeResult.LimitReached
            val inserted = UsageEvents.insertIgnore {
                it[UsageEvents.userId] = userId
                it[UsageEvents.kind] = kind
                it[UsageEvents.refId] = refId
                it[refunded] = false
                it[createdAt] = now.toOffset()
            }.insertedCount
            // Otro usuario pudo insertar el mismo (kind, ref_id) entre la lectura y el INSERT.
            if (inserted == 1) ChargeResult.Charged else existing(kind, refId) ?: error("usage_events inconsistente para $kind/$refId")
        }

    override fun countActive(userId: UUID, kind: QuotaKind, window: QuotaWindow): Int = transaction(db) { active(userId, kind, window) }

    override fun refund(kind: QuotaKind, refId: String): Boolean = transaction(db) {
        UsageEvents.update({ (UsageEvents.kind eq kind) and (UsageEvents.refId eq refId) }) {
            it[refunded] = true
        } > 0
    }

    private fun existing(kind: QuotaKind, refId: String): ChargeResult.AlreadyCharged? =
        UsageEvents.selectAll().where { (UsageEvents.kind eq kind) and (UsageEvents.refId eq refId) }
            .singleOrNull()
            ?.let { ChargeResult.AlreadyCharged(it[UsageEvents.userId], it[UsageEvents.refunded]) }

    private fun active(userId: UUID, kind: QuotaKind, window: QuotaWindow): Int =
        UsageEvents.selectAll().where {
            (UsageEvents.userId eq userId) and (UsageEvents.kind eq kind) and (UsageEvents.refunded eq false) and
                (UsageEvents.createdAt greaterEq window.start.toOffset()) and (UsageEvents.createdAt less window.end.toOffset())
        }.count().toInt()
}

internal fun Instant.toOffset(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)
