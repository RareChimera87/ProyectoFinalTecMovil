package me.santiagoreina.parcheu.api.admin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.identity.toKotlin
import me.santiagoreina.parcheu.api.platform.db.tables.Reports
import me.santiagoreina.parcheu.contract.AdminReport
import me.santiagoreina.parcheu.contract.ReportKind
import me.santiagoreina.parcheu.contract.ReportStatus
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** Reportes sobre Postgres (arquitectura.md §4, tabla `reports`; el índice cubre `status, priority, created_at`). */
class ExposedAdminRepository(private val db: Database) : AdminRepository {
    private suspend fun <T> tx(block: JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) { transaction(db) { block() } }

    override suspend fun reports(kind: ReportKind?, status: ReportStatus, offset: Int, limit: Int): List<AdminReport> = tx {
        val query = Reports.selectAll().where {
            val byStatus = Reports.status eq status
            if (kind == null) byStatus else byStatus and (Reports.kind eq kind)
        }
        query
            .orderBy(Reports.priority to SortOrder.DESC, Reports.createdAt to SortOrder.ASC, Reports.id to SortOrder.ASC)
            .limit(limit).offset(offset.toLong())
            .map { it.toAdminReport() }
    }

    override suspend fun report(id: UUID): AdminReport? = tx {
        Reports.selectAll().where { Reports.id eq id }.singleOrNull()?.toAdminReport()
    }

    override suspend fun markResolved(id: UUID, resolution: String, resolvedBy: UUID?, now: Instant): Boolean = tx {
        Reports.update({ (Reports.id eq id) and (Reports.status eq ReportStatus.OPEN) }) {
            it[Reports.status] = ReportStatus.RESOLVED
            it[Reports.resolution] = resolution
            it[Reports.resolvedBy] = resolvedBy
            it[resolvedAt] = OffsetDateTime.ofInstant(now, ZoneOffset.UTC)
        } == 1
    }
}

private fun ResultRow.toAdminReport() = AdminReport(
    id = this[Reports.id].toString(),
    kind = this[Reports.kind],
    status = this[Reports.status],
    source = this[Reports.reportSource],
    reason = this[Reports.reason],
    details = this[Reports.details],
    priority = this[Reports.priority].toInt(),
    reporterId = this[Reports.reporterId]?.toString(),
    targetId = this[Reports.targetId]?.toString(),
    chatId = this[Reports.chatId]?.toString(),
    messageId = this[Reports.messageId]?.toString(),
    simulationId = this[Reports.simulationId]?.toString(),
    turnIndex = this[Reports.turnIndex]?.toInt(),
    snapshot = this[Reports.snapshot],
    resolution = this[Reports.resolution],
    createdAt = this[Reports.createdAt].toInstant().toKotlin(),
    resolvedAt = this[Reports.resolvedAt]?.toInstant()?.toKotlin(),
)
