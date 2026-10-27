package me.santiagoreina.parcheu.api.admin

import me.santiagoreina.parcheu.api.chat.ChatService
import me.santiagoreina.parcheu.api.identity.IdentityRepository
import me.santiagoreina.parcheu.api.platform.errors.Conflict
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.contract.AdminAction
import me.santiagoreina.parcheu.contract.AdminReport
import me.santiagoreina.parcheu.contract.Page
import me.santiagoreina.parcheu.contract.ReportKind
import me.santiagoreina.parcheu.contract.ReportStatus
import me.santiagoreina.parcheu.contract.Resolve
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Administración de reportes (E8.5, arquitectura.md §5.2 y §10). Solo la llaman las rutas tras
 * comprobar el claim `admin`. `ban` no tiene columna propia: es una suspensión hasta [BAN_UNTIL], que
 * la sesión, el chat y la fila ya respetan (`suspended_until`).
 */
class AdminService(
    private val repo: AdminRepository,
    private val identity: IdentityRepository,
    private val chat: ChatService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** `GET /v1/admin/reports?kind=&status=&cursor=&limit=`: por defecto los OPEN, de mayor prioridad. */
    suspend fun reports(kind: ReportKind?, status: ReportStatus, cursor: String?, limit: Int?): Page<AdminReport> {
        val offset = if (cursor == null) 0 else cursor.toIntOrNull()?.takeIf { it >= 0 } ?: throw ValidationFailed("cursor no es válido")
        val size = (limit ?: DEFAULT_LIMIT).also { if (it !in 1..MAX_LIMIT) throw ValidationFailed("limit debe estar entre 1 y $MAX_LIMIT") }
        val rows = repo.reports(kind, status, offset, size + 1)
        return Page(rows.take(size), nextCursor = if (rows.size > size) (offset + size).toString() else null)
    }

    /**
     * `POST /v1/admin/reports/{id}/resolve`. Valida la acción contra el reporte antes de tocar nada, la
     * aplica y marca el reporte RESOLVED. Un reporte ya resuelto es 409. La acción se aplica solo si
     * este llamado ganó la marca de resuelto, así dos admins a la vez no la repiten.
     *
     * - DISMISS: solo cierra el reporte.
     * - HIDE_MESSAGE: oculta `messageId` (Postgres y Firestore); el reporte debe traerlo.
     * - SUSPEND_24H / BAN: necesitan `targetId` (los AI_CONTENT no tienen a quién sancionar).
     */
    suspend fun resolve(adminUserId: UUID?, reportId: String, request: Resolve): AdminReport {
        val id = runCatching { UUID.fromString(reportId) }.getOrNull() ?: throw NotFound("El reporte no existe")
        val report = repo.report(id) ?: throw NotFound("El reporte no existe")
        if (report.status == ReportStatus.RESOLVED) throw Conflict("El reporte ya está resuelto")
        val note = request.note?.trim()?.takeIf { it.isNotEmpty() }
        if (note != null && note.codePointCount(0, note.length) > MAX_NOTE) throw ValidationFailed("La nota admite máximo $MAX_NOTE caracteres")

        val target = report.targetId?.let { UUID.fromString(it) }
        val message = report.messageId?.let { UUID.fromString(it) }
        when (request.action) {
            AdminAction.DISMISS -> Unit
            AdminAction.HIDE_MESSAGE -> if (message == null) throw ValidationFailed("El reporte no trae un mensaje que ocultar")
            AdminAction.SUSPEND_24H, AdminAction.BAN -> if (target == null) throw ValidationFailed("El reporte no tiene a quién sancionar")
        }

        val now = clock.instant()
        val resolution = listOfNotNull(request.action.name, note).joinToString(": ")
        if (!repo.markResolved(id, resolution, adminUserId, now)) throw Conflict("El reporte ya está resuelto")
        applyAction(request.action, target, message, now)

        // Solo ids y acción, nunca el detalle ni los textos del reporte (dato personal).
        log.atInfo()
            .setMessage(REPORT_RESOLVED)
            .addKeyValue("event", REPORT_RESOLVED)
            .addKeyValue("reportId", reportId)
            .addKeyValue("action", request.action.name)
            .addKeyValue("adminUserId", adminUserId?.toString())
            .log()
        return repo.report(id) ?: throw NotFound("El reporte no existe")
    }

    private suspend fun applyAction(action: AdminAction, target: UUID?, message: UUID?, now: Instant) {
        when (action) {
            AdminAction.DISMISS -> Unit
            AdminAction.HIDE_MESSAGE -> chat.hideMessage(requireNotNull(message))
            AdminAction.SUSPEND_24H -> identity.suspendUntil(requireNotNull(target), now.plus(SUSPENSION), now)
            AdminAction.BAN -> identity.suspendUntil(requireNotNull(target), BAN_UNTIL, now)
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 100
        const val MAX_NOTE = 500
        const val REPORT_RESOLVED = "report_resolved"
        val SUSPENSION: Duration = Duration.ofHours(24)

        /** Fecha tope del baneo: la sesión y las escrituras lo ven como suspensión vigente. */
        val BAN_UNTIL: Instant = Instant.parse("2999-12-31T00:00:00Z")
    }
}
