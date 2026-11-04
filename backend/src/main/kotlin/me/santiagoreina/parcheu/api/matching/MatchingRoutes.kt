package me.santiagoreina.parcheu.api.matching

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
import me.santiagoreina.parcheu.contract.Choice
import me.santiagoreina.parcheu.contract.Enqueue
import java.util.UUID

/**
 * Rutas del dominio matching (E4.2): la fila `/v1/match/queue`, los matches `/v1/matches/{id}` y el
 * barrido `/internal/jobs/match-sweep` (Cloud Scheduler).
 *
 * [viewerId] traduce el usuario de Firebase a `users.id` (lo resuelve identity).
 */
class MatchingRoutes(
    private val matching: MatchingModule,
    private val viewerId: suspend (FirebaseUser) -> UUID,
) : DomainRoutes {
    override fun Route.v1() {
        post("/match/queue") {
            call.respond(matching.service.enqueue(viewerId(call.firebaseUser()), call.receive<Enqueue>().mode))
        }
        get("/match/queue") { call.respond(matching.service.ticket(viewerId(call.firebaseUser()))) }
        delete("/match/queue") {
            matching.service.cancel(viewerId(call.firebaseUser()))
            call.respond(HttpStatusCode.NoContent)
        }
        get("/matches/{id}") {
            call.respond(matching.service.match(viewerId(call.firebaseUser()), call.parameters["id"] ?: throw NotFound()))
        }
        post("/matches/{id}/choice") {
            val choice = call.receive<Choice>().choice
            call.respond(matching.service.choose(viewerId(call.firebaseUser()), call.parameters["id"] ?: throw NotFound(), choice))
        }
    }

    override fun Route.internal() {
        post("/jobs/match-sweep") {
            matching.service.sweep()
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun ApplicationCall.firebaseUser(): FirebaseUser = principal<FirebaseUser>() ?: throw Unauthenticated()
