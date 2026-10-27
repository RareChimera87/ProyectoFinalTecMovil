package me.santiagoreina.parcheu.api.admin

import me.santiagoreina.parcheu.api.identity.toKotlin
import me.santiagoreina.parcheu.api.safety.NewReport
import me.santiagoreina.parcheu.api.safety.snapshotJson
import me.santiagoreina.parcheu.contract.AdminReport
import me.santiagoreina.parcheu.contract.ReportKind
import me.santiagoreina.parcheu.contract.ReportSource
import me.santiagoreina.parcheu.contract.ReportStatus
import java.time.Instant
import java.util.UUID

/**
 * [AdminRepository] en memoria: lee los reportes que guarda el repositorio de safety (el `source` de
 * [reports]) y lleva aparte el estado de resolución. Para pruebas de rutas y `ENV=local`.
 */
class InMemoryAdminRepository(private val reports: () -> List<NewReport>) : AdminRepository {
    private data class Resolved(val resolution: String, val at: Instant)

    private val lock = Any()
    private val resolved = mutableMapOf<UUID, Resolved>()

    override suspend fun reports(kind: ReportKind?, status: ReportStatus, offset: Int, limit: Int): List<AdminReport> =
        all().filter { it.status == status && (kind == null || it.kind == kind) }
            .sortedWith(compareByDescending<AdminReport> { it.priority }.thenBy { it.createdAt }.thenBy { it.id })
            .drop(offset).take(limit)

    override suspend fun report(id: UUID): AdminReport? = all().firstOrNull { it.id == id.toString() }

    override suspend fun markResolved(id: UUID, resolution: String, resolvedBy: UUID?, now: Instant): Boolean = synchronized(lock) {
        if (reports().none { it.id == id } || id in resolved) return false
        resolved[id] = Resolved(resolution, now)
        true
    }

    private fun all(): List<AdminReport> {
        val state = synchronized(lock) { resolved.toMap() }
        return reports().map { r ->
            val done = state[r.id]
            AdminReport(
                id = r.id.toString(),
                kind = r.kind,
                status = if (done != null) ReportStatus.RESOLVED else ReportStatus.OPEN,
                source = ReportSource.USER,
                reason = r.reason,
                details = r.details,
                priority = r.priority,
                reporterId = r.reporterId.toString(),
                targetId = r.targetId?.toString(),
                chatId = r.chatId?.toString(),
                messageId = r.messageId?.toString(),
                simulationId = r.simulationId?.toString(),
                turnIndex = r.turnIndex,
                snapshot = r.snapshot?.let(::snapshotJson),
                resolution = done?.resolution,
                createdAt = r.createdAt.toKotlin(),
                resolvedAt = done?.at?.toKotlin(),
            )
        }
    }
}
