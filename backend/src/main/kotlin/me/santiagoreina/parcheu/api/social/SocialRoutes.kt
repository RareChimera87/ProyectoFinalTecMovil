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
import me.santiagoreina.parcheu.api.platform.stubGet
import me.santiagoreina.parcheu.api.platform.stubPost
import me.santiagoreina.parcheu.api.social.invites.InviteService
import me.santiagoreina.parcheu.contract.Redeem
import java.util.UUID

/**
 * Rutas del dominio social. Las invitaciones (E6.1) ya están; las presentaciones siguen como stubs 501
 * de E0.3 hasta E6.2. [currentUserId] traduce el usuario de Firebase a `users.id`.
 */
class SocialRoutes(
    private val invites: InviteService,
    private val currentUserId: suspend (FirebaseUser) -> UUID,
) : DomainRoutes {
    override fun Route.v1() {
        stubPost("/introductions")
        stubGet("/introductions/{id}")
        stubPost("/introductions/{id}/response")
        stubPost("/introductions/request")
        get("/invites/me") {
            call.respond(invites.me(call.userId()))
        }
        post("/invites/redeem") {
            val user = call.userId()
            call.respond(invites.redeem(user, call.receive<Redeem>()))
        }
        post("/invites/{userId}/accept") {
            val target = call.parameters["userId"]?.takeIf { it.isNotBlank() } ?: throw NotFound("No hay una invitación pendiente de esa persona")
            call.respond(invites.accept(call.userId(), target))
        }
    }

    private suspend fun ApplicationCall.userId(): UUID = currentUserId(principal<FirebaseUser>() ?: throw Unauthenticated())
}
