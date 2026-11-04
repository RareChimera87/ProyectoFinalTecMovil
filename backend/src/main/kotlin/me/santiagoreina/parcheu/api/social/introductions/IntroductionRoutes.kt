package me.santiagoreina.parcheu.api.social.introductions

import io.ktor.http.HttpStatusCode
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
import me.santiagoreina.parcheu.contract.IntroRequest
import me.santiagoreina.parcheu.contract.IntroResponse
import me.santiagoreina.parcheu.contract.Introduce
import java.util.UUID

/** Rutas de presentaciones (E6.2, arquitectura.md §5.2). Las invitaciones siguen en `SocialRoutes`. */
class IntroductionRoutes(
    private val service: IntroductionService,
    private val currentUserId: suspend (FirebaseUser) -> UUID,
) : DomainRoutes {
    override fun Route.v1() {
        post("/introductions") {
            val user = call.userId()
            call.respond(HttpStatusCode.Accepted, service.introduce(user, call.receive<Introduce>()))
        }
        post("/introductions/request") {
            val user = call.userId()
            service.request(user, call.receive<IntroRequest>())
            call.respond(HttpStatusCode.Accepted)
        }
        get("/introductions/{id}") {
            call.respond(service.get(call.userId(), call.introId()))
        }
        post("/introductions/{id}/response") {
            val user = call.userId()
            call.respond(service.respond(user, call.introId(), call.receive<IntroResponse>()))
        }
    }

    private suspend fun ApplicationCall.userId(): UUID = currentUserId(principal<FirebaseUser>() ?: throw Unauthenticated())

    private fun ApplicationCall.introId(): String = parameters["id"]?.takeIf { it.isNotBlank() } ?: throw NotFound()
}
