package me.santiagoreina.parcheu.api.scenario

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.request.receive
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.Unauthenticated
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.api.twin.TaskPayload
import me.santiagoreina.parcheu.contract.Mode
import java.util.UUID

/**
 * Rutas del modo escenario (E4.8, arquitectura.md §5.2): lista de escenarios, estado y acuerdo de la sesión, la
 * tarea `judge-scenario` (cola `simulations`) y el barrido `scenario-sweep`.
 *
 * [viewerId] traduce el usuario de Firebase a `users.id` (lo resuelve identity).
 */
class ScenarioRoutes(
    private val scenario: ScenarioService,
    private val viewerId: suspend (FirebaseUser) -> UUID,
) : DomainRoutes {
    override fun Route.v1() {
        get("/scenarios") {
            val mode = call.request.queryParameters["mode"]?.let { raw ->
                Mode.entries.firstOrNull { it.name == raw.trim().uppercase() } ?: throw ValidationFailed("mode no es válido")
            }
            call.respond(scenario.list(viewerId(call.firebaseUser()), mode))
        }
        get("/scenario-sessions/{chatId}") {
            call.respond(scenario.session(viewerId(call.firebaseUser()), call.parameters["chatId"] ?: throw NotFound()))
        }
        post("/scenario-sessions/{chatId}/agreement") {
            call.respond(scenario.agree(viewerId(call.firebaseUser()), call.parameters["chatId"] ?: throw NotFound()))
        }
    }

    override fun Route.internal() {
        post(ScenarioService.JUDGE_PATH) {
            scenario.judge(call.receive<TaskPayload>().uuid())
            call.respond(HttpStatusCode.NoContent)
        }
        post(ScenarioService.SWEEP_PATH) {
            scenario.sweep()
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun ApplicationCall.firebaseUser(): FirebaseUser = principal<FirebaseUser>() ?: throw Unauthenticated()
