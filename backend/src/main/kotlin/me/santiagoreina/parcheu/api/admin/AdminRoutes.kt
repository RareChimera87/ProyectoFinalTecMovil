package me.santiagoreina.parcheu.api.admin

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.errors.Forbidden
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.Unauthenticated
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.contract.ReportKind
import me.santiagoreina.parcheu.contract.ReportStatus
import me.santiagoreina.parcheu.contract.Resolve
import java.util.UUID

/**
 * Rutas de administración (E8.5, arquitectura.md §5.2). Todas exigen el claim `admin` del ID token
 * (403 FORBIDDEN sin él). [adminUserId] traduce al admin a su `users.id` para `resolved_by`; devuelve
 * null si el admin no tiene cuenta en la app (el claim basta para moderar).
 */
class AdminRoutes(
    private val admin: AdminService,
    private val adminUserId: suspend (FirebaseUser) -> UUID?,
) : DomainRoutes {
    override fun Route.v1() {
        get("/admin/reports") {
            call.requireAdmin()
            val params = call.request.queryParameters
            val kind = params["kind"]?.let { raw -> enumOrNull<ReportKind>(raw) ?: throw ValidationFailed("kind debe ser USER o AI_CONTENT") }
            val status = params["status"]?.let { raw -> enumOrNull<ReportStatus>(raw) ?: throw ValidationFailed("status debe ser OPEN o RESOLVED") }
                ?: ReportStatus.OPEN
            val limit = params["limit"]?.let { it.toIntOrNull() ?: throw ValidationFailed("limit no es válido") }
            call.respond(admin.reports(kind, status, params["cursor"], limit))
        }
        post("/admin/reports/{id}/resolve") {
            val user = call.requireAdmin()
            val id = call.parameters["id"] ?: throw NotFound("El reporte no existe")
            call.respond(admin.resolve(adminUserId(user), id, call.receive<Resolve>()))
        }
    }

    private fun ApplicationCall.requireAdmin(): FirebaseUser {
        val user = principal<FirebaseUser>() ?: throw Unauthenticated()
        if (!user.admin) throw Forbidden("Se necesita el claim admin")
        return user
    }

    private inline fun <reified E : Enum<E>> enumOrNull(raw: String): E? = enumValues<E>().firstOrNull { it.name == raw }
}
