package me.santiagoreina.parcheu.api.graph.communities

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
import me.santiagoreina.parcheu.api.platform.errors.Unauthenticated
import me.santiagoreina.parcheu.api.twin.TaskPayload
import java.util.UUID

/**
 * Rutas de comunidades (E6.4): `GET /v1/communities`, la tarea `name-community` y el job semanal
 * `POST /internal/jobs/communities` (Scheduler, o el programador en proceso en el homeserver).
 */
class CommunityRoutes(
    private val communities: CommunityModule,
    private val viewerId: suspend (FirebaseUser) -> UUID,
) : DomainRoutes {
    override fun Route.v1() {
        get("/communities") {
            call.respond(communities.view.communitiesDto(viewerId(call.firebaseUser())))
        }
    }

    override fun Route.internal() {
        post(CommunityService.NAME_PATH) {
            communities.service.nameCommunity(call.receive<TaskPayload>().uuid())
            call.respond(HttpStatusCode.NoContent)
        }
        post("/jobs/communities") {
            communities.service.runWeekly()
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun ApplicationCall.firebaseUser(): FirebaseUser = principal<FirebaseUser>() ?: throw Unauthenticated()
