package me.santiagoreina.parcheu.api.social

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.Unauthenticated
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.api.platform.stubGet
import me.santiagoreina.parcheu.api.platform.stubPost
import me.santiagoreina.parcheu.api.social.invites.InviteService
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Redeem
import java.util.UUID

/**
 * Rutas del dominio social. Las invitaciones (E6.1) viven aquí; las presentaciones (E6.2) en
 * `introductions/IntroductionRoutes`. [currentUserId] traduce el usuario de Firebase a `users.id`.
 */
class SocialRoutes(
    private val invites: InviteService,
    private val currentUserId: suspend (FirebaseUser) -> UUID,
) : DomainRoutes {
    override fun Route.v1() {
        get("/invites/me") {
            call.respond(invites.me(call.userId()))
        }
        post("/invites/redeem") {
            val user = call.userId()
            call.respond(invites.redeem(user, call.receive<Redeem>()))
        }
        post("/invites/{userId}/accept") {
            val target = call.parameters["userId"]?.takeIf { it.isNotBlank() } ?: throw NotFound("No hay una invitación pendiente de esa persona")
            val mode = call.request.queryParameters["mode"]?.let { raw ->
                Mode.entries.firstOrNull { it.name == raw.trim().uppercase() } ?: throw ValidationFailed("Modo desconocido: $raw")
            }
            call.respond(invites.accept(call.userId(), target, mode))
        }
    }

    private suspend fun ApplicationCall.userId(): UUID = currentUserId(principal<FirebaseUser>() ?: throw Unauthenticated())
}
