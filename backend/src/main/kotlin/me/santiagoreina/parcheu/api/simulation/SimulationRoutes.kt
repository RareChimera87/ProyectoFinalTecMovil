package me.santiagoreina.parcheu.api.simulation

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
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.api.platform.stubPost
import me.santiagoreina.parcheu.api.twin.TaskPayload
import me.santiagoreina.parcheu.contract.Decision
import me.santiagoreina.parcheu.contract.StartSimulation
import java.util.UUID

/**
 * Rutas del dominio simulation. E4.3 implementa crear, listar, consultar, transcripción y la tarea
 * `simulate`; E4.6 el informe, su desbloqueo y la decisión; `judge-scenario` es de E4.8.
 *
 * [viewerId] traduce el usuario de Firebase a `users.id` (lo resuelve identity).
 */
class SimulationRoutes(
    private val simulation: SimulationModule,
    private val viewerId: suspend (FirebaseUser) -> UUID,
) : DomainRoutes {
    override fun Route.v1() {
        post("/simulations") {
            val summary = simulation.service.start(viewerId(call.firebaseUser()), call.receive<StartSimulation>())
            call.respond(HttpStatusCode.Accepted, summary)
        }
        get("/simulations") {
            val params = call.request.queryParameters
            val limit = params["limit"]?.let { it.toIntOrNull() ?: throw ValidationFailed("limit debe ser un número") }
            call.respond(simulation.service.list(viewerId(call.firebaseUser()), params["cursor"], limit))
        }
        get("/simulations/{id}") {
            call.respond(simulation.service.get(viewerId(call.firebaseUser()), call.parameters["id"] ?: throw NotFound()))
        }
        get("/simulations/{id}/transcript") {
            call.respond(simulation.service.transcript(viewerId(call.firebaseUser()), call.parameters["id"] ?: throw NotFound()))
        }
        get("/simulations/{id}/report") {
            call.respond(simulation.report.report(viewerId(call.firebaseUser()), call.parameters["id"] ?: throw NotFound()))
        }
        post("/simulations/{id}/report:unlock") {
            call.respond(simulation.report.unlock(viewerId(call.firebaseUser()), call.parameters["id"] ?: throw NotFound()))
        }
        post("/simulations/{id}/decision") {
            val body = call.receive<Decision>()
            call.respond(simulation.report.decide(viewerId(call.firebaseUser()), call.parameters["id"] ?: throw NotFound(), body))
        }
    }

    override fun Route.internal() {
        post("/tasks/simulate") {
            simulation.engine.run(call.receive<TaskPayload>().uuid())
            call.respond(HttpStatusCode.NoContent)
        }
        stubPost("/tasks/judge-scenario")
    }
}

private fun ApplicationCall.firebaseUser(): FirebaseUser = principal<FirebaseUser>() ?: throw Unauthenticated()
