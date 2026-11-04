package me.santiagoreina.parcheu.api.platform.db.tables

import me.santiagoreina.parcheu.contract.QuotaKind
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/* Notificaciones in-app y eventos de uso para cuotas (§4, ADR-0010). */

object Notifications : Table("notifications") {
    val id = uuidPk()
    val userId = userRef("user_id", ReferenceOption.CASCADE)
    /** Abierto a propósito: los valores conocidos están en `NotificationKinds` del contrato. */
    val kind = text("kind")
    val payload = jsonbColumn("payload").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val readAt = timestampWithTimeZone("read_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** Un cobro de cupo por `(kind, ref_id)`: la unicidad hace idempotente el cobro. */
object UsageEvents : Table("usage_events") {
    val id = uuidPk()
    val userId = userRef("user_id", ReferenceOption.CASCADE)
    val kind = enumText<QuotaKind>("kind")
    val refId = text("ref_id")
    val refunded = bool("refunded").default(false)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

/** Topes diarios internos por persona (`quota.DailyLimiter`, B-M1): `kind` es un `quota.DailyKind`. */
object DailyCounters : Table("daily_counters") {
    val userId = userRef("user_id", ReferenceOption.CASCADE)
    /** Día en America/Bogota. */
    val day = date("day")
    val kind = text("kind")
    val count = integer("count").default(0)
    override val primaryKey = PrimaryKey(userId, day, kind)
}
