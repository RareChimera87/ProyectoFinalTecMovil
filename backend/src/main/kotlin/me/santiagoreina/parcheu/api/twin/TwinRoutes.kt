package me.santiagoreina.parcheu.api.twin

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
import me.santiagoreina.parcheu.api.platform.stubGet
import me.santiagoreina.parcheu.api.platform.stubPost
import me.santiagoreina.parcheu.contract.Answers
import java.util.UUID

/**
 * Rutas del dominio twin. E2.3 implementa preguntas, respuestas, gemelo, evolución y la tarea
 * `build-persona`; memorias y feedback siguen como stub 501 hasta E9.x, E3.x y los jobs de E9.2.
 */
class TwinRoutes(private val twin: TwinModule) : DomainRoutes {
    override fun Route.v1() {
        get("/twin/questions") { call.respond(twin.service.questions()) }
        post("/twin/answers") { call.respond(twin.service.submitAnswers(call.userId(twin.users), call.receive<Answers>())) }
        get("/twin") { call.respond(twin.service.twin(call.userId(twin.users))) }
        get("/twin/evolution") { call.respond(twin.service.evolution(call.userId(twin.users))) }

        stubGet("/twin/memories")
        stubPost("/twin/memories/{id}/accept")
        stubPost("/twin/memories/{id}/reject")
        stubGet("/twin/feedback-prompts")
        stubPost("/twin/feedback")
    }

    override fun Route.internal() {
        post(TwinService.BUILD_PERSONA_PATH) {
            twin.service.buildPersona(call.receive<TaskPayload>().uuid())
            call.respond(HttpStatusCode.NoContent)
        }

        stubPost("/tasks/extract-memories")
        stubPost("/jobs/memories")
        stubPost("/jobs/twin-accuracy")
    }
}

private fun ApplicationCall.userId(users: TwinUserLookup): UUID {
    val user = principal<FirebaseUser>() ?: throw Unauthenticated()
    return users.userIdFor(user.uid) ?: throw NotFound("Usuario sin sesión: llama primero a POST /v1/session")
}
