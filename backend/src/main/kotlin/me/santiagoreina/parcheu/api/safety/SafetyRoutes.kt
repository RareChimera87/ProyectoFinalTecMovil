package me.santiagoreina.parcheu.api.safety

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.Unauthenticated
import me.santiagoreina.parcheu.api.safety.moderation.ModerationService
import me.santiagoreina.parcheu.api.twin.TaskPayload
import me.santiagoreina.parcheu.contract.ReportRequest
import java.util.UUID

/**
 * Rutas del dominio safety (E8.1, arquitectura.md §5.2). [currentUserId] traduce el usuario de
 * Firebase a `users.id`. La tarea interna `/tasks/moderate` es la moderación con IA (E8.6).
 */
class SafetyRoutes(
    private val safety: SafetyService,
    private val moderation: ModerationService,
    private val currentUserId: suspend (FirebaseUser) -> UUID,
) : DomainRoutes {
    override fun Route.v1() {
        post("/reports") {
            val user = call.userId()
            call.respond(HttpStatusCode.Created, safety.report(user, call.receive<ReportRequest>()))
        }
        get("/blocks") {
            call.respond(safety.blocks(call.userId()))
        }
        post("/blocks/{userId}") {
            safety.block(call.userId(), call.target())
            call.respond(HttpStatusCode.NoContent)
        }
        delete("/blocks/{userId}") {
            safety.unblock(call.userId(), call.target())
            call.respond(HttpStatusCode.NoContent)
        }
    }

    override fun Route.internal() {
        // Moderación con IA (E8.6): fail-open, así que responde 204 aunque el LLM falle.
        post(ModerationService.TASK_PATH) {
            moderation.moderate(call.receive<TaskPayload>().uuid())
            call.respond(HttpStatusCode.NoContent)
        }
        // Reintento del aviso de ayuda por autolesión, sin LLM (E8.6).
        post(ModerationService.HELP_TASK_PATH) {
            moderation.deliverHelp(call.receive<TaskPayload>().uuid())
            call.respond(HttpStatusCode.NoContent)
        }
    }

    private suspend fun ApplicationCall.userId(): UUID = currentUserId(principal<FirebaseUser>() ?: throw Unauthenticated())

    private fun ApplicationCall.target(): String = parameters["userId"]?.takeIf { it.isNotBlank() } ?: throw NotFound("La persona no existe")
}
