package me.santiagoreina.parcheu.api.platform.db.tables

import me.santiagoreina.parcheu.contract.ReportKind
import me.santiagoreina.parcheu.contract.ReportReason
import me.santiagoreina.parcheu.contract.ReportSource
import me.santiagoreina.parcheu.contract.ReportStatus
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/* Seguridad: bloqueos y reportes (§4, §10.1). */

object Blocks : Table("blocks") {
    val blockerId = userRef("blocker_id", ReferenceOption.CASCADE)
    val blockedId = userRef("blocked_id", ReferenceOption.CASCADE)
    val createdAt = timestampWithTimeZone("created_at")
    val wasConnected = bool("was_connected").default(false)
    override val primaryKey = PrimaryKey(blockerId, blockedId)
}

object Reports : Table("reports") {
    val id = uuidPk()
    /** Nulo si `source = AUTO`. */
    val reporterId = userRef("reporter_id").nullable()
    /** Nulo si `kind = AI_CONTENT`. */
    val targetId = userRef("target_id").nullable()
    val chatId = javaUUID("chat_id").references(Chats.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val messageId = javaUUID("message_id").nullable()
    val kind = enumText<ReportKind>("kind").default(ReportKind.USER)
    val simulationId = javaUUID("simulation_id").references(Simulations.id).nullable()
    val turnIndex = short("turn_index").nullable()
    val reason = enumText<ReportReason>("reason")
    val details = text("details").nullable()
    /** Instantánea de hasta 20 mensajes: dato personal (PersonalJsonPaths). */
    val snapshot = jsonbColumn("snapshot").nullable()
    val reportSource = enumText<ReportSource>("source").default(ReportSource.USER)
    val priority = short("priority").default(0)
    val status = enumText<ReportStatus>("status").default(ReportStatus.OPEN)
    val resolution = text("resolution").nullable()
    val resolvedBy = userRef("resolved_by").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val resolvedAt = timestampWithTimeZone("resolved_at").nullable()
    override val primaryKey = PrimaryKey(id)
}
