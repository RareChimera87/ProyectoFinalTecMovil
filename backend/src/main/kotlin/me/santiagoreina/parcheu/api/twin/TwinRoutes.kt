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
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.api.platform.stubGet
import me.santiagoreina.parcheu.api.platform.stubPost
import me.santiagoreina.parcheu.api.twin.memories.MemoryService
import me.santiagoreina.parcheu.contract.Answers
import java.util.UUID

/**
 * Rutas del dominio twin. E2.3 implementa preguntas, respuestas, gemelo, evolución y la tarea
 * `build-persona`; E9.2 las memorias (API, tarea `extract-memories` y job `memories`). El feedback y
 * el job `twin-accuracy` siguen como stub 501 hasta E9.3.
 */
class TwinRoutes(private val twin: TwinModule) : DomainRoutes {
    override fun Route.v1() {
        get("/twin/questions") { call.respond(twin.service.questions()) }
        post("/twin/answers") { call.respond(twin.service.submitAnswers(call.userId(twin.users), call.receive<Answers>())) }
        get("/twin") { call.respond(twin.service.twin(call.userId(twin.users))) }
        get("/twin/evolution") { call.respond(twin.service.evolution(call.userId(twin.users))) }

        get("/twin/memories") {
            val q = call.request.queryParameters
            call.respond(twin.memories.list(call.userId(twin.users), q["status"], q["cursor"], q.limit()))
        }
        post("/twin/memories/{id}/accept") { call.respond(twin.memories.accept(call.userId(twin.users), call.parameters["id"].orEmpty())) }
        post("/twin/memories/{id}/reject") { call.respond(twin.memories.reject(call.userId(twin.users), call.parameters["id"].orEmpty())) }
        stubGet("/twin/feedback-prompts")
        stubPost("/twin/feedback")
    }

    override fun Route.internal() {
        post(TwinService.BUILD_PERSONA_PATH) {
            twin.service.buildPersona(call.receive<TaskPayload>().uuid())
            call.respond(HttpStatusCode.NoContent)
        }

        post(MemoryService.TASK_PATH) {
            twin.memories.extract(call.receive<TaskPayload>().uuid())
            call.respond(HttpStatusCode.NoContent)
        }
        post(MemoryService.JOB_PATH) {
            twin.memories.enqueueNightly()
            call.respond(HttpStatusCode.NoContent)
        }
        stubPost("/jobs/twin-accuracy")
    }
}

private fun io.ktor.http.Parameters.limit(): Int? =
    this["limit"]?.let { it.toIntOrNull() ?: throw ValidationFailed("limit debe ser un número") }

private fun ApplicationCall.userId(users: TwinUserLookup): UUID {
    val user = principal<FirebaseUser>() ?: throw Unauthenticated()
    return users.userIdFor(user.uid) ?: throw NotFound("Usuario sin sesión: llama primero a POST /v1/session")
}
