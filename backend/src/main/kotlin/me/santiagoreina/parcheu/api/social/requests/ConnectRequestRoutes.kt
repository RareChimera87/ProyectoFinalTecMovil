package me.santiagoreina.parcheu.api.social.requests

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.errors.Unauthenticated
import java.util.UUID

/** `GET /v1/connect-requests` (E6.6, arquitectura.md §5.2). */
class ConnectRequestRoutes(
    private val service: ConnectRequestService,
    private val currentUserId: suspend (FirebaseUser) -> UUID,
) : DomainRoutes {
    override fun Route.v1() {
        get("/connect-requests") {
            call.respond(service.requests(call.userId()))
        }
    }

    private suspend fun ApplicationCall.userId(): UUID = currentUserId(principal<FirebaseUser>() ?: throw Unauthenticated())
}
