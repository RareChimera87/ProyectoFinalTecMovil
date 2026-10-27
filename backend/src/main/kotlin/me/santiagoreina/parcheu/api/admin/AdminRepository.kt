package me.santiagoreina.parcheu.api.admin

import me.santiagoreina.parcheu.contract.AdminReport
import me.santiagoreina.parcheu.contract.ReportKind
import me.santiagoreina.parcheu.contract.ReportStatus
import java.time.Instant
import java.util.UUID

/**
 * Acceso a datos de la administración de reportes (E8.5). [ExposedAdminRepository] lee y actualiza la
 * tabla `reports`; [InMemoryAdminRepository] replica la semántica para pruebas de rutas y `ENV=local`.
 * Suspender usuarios y ocultar mensajes no pasan por aquí: los hacen `IdentityRepository` y
 * `ChatService`, que son quienes leen esos estados.
 */
interface AdminRepository {
    /**
     * Reportes con [status] (y [kind] si no es null), primero los de mayor prioridad y, a igual
     * prioridad, los más antiguos. Devuelve hasta [limit] desde la posición [offset].
     */
    suspend fun reports(kind: ReportKind?, status: ReportStatus, offset: Int, limit: Int): List<AdminReport>

    suspend fun report(id: UUID): AdminReport?

    /**
     * Pasa el reporte de OPEN a RESOLVED con [resolution] y quién lo resolvió ([resolvedBy] es null si
     * el admin no tiene cuenta en la app). Devuelve false si ya estaba resuelto o no existe, de modo que
     * dos admins que resuelven a la vez no aplican la acción dos veces.
     */
    suspend fun markResolved(id: UUID, resolution: String, resolvedBy: UUID?, now: Instant): Boolean
}
