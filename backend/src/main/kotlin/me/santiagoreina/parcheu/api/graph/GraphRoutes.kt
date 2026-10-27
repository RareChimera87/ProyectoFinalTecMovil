package me.santiagoreina.parcheu.api.graph

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import me.santiagoreina.parcheu.api.graph.fof.FofService
import me.santiagoreina.parcheu.api.graph.history.HistoryService
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.Unauthenticated
import me.santiagoreina.parcheu.api.platform.stubGet
import me.santiagoreina.parcheu.api.platform.stubPost
import java.util.UUID

/**
 * Rutas del dominio graph. E3.1 implementa el ego-grafo y el detalle de nodo y E6.3 amigos de
 * amigos ([FofService]) y E6.5 la evolución de la red ([HistoryService]); comunidades (E6.2) y connect-requests siguen como stub 501
 * hasta su feature.
 *
 * [viewerId] traduce el usuario de Firebase a `users.id` (lo resuelve identity).
 */
class GraphRoutes(
    private val graph: GraphService,
    private val fof: FofService,
    private val history: HistoryService,
    private val viewerId: suspend (FirebaseUser) -> UUID,
) : DomainRoutes {
    override fun Route.v1() {
        get("/graph") {
            val params = call.request.queryParameters
            val filter = GraphService.parseFilter(params["mode"], params["minAffinity"], params["community"])
            call.respond(graph.graph(viewerId(call.firebaseUser()), filter))
        }
        get("/graph/nodes/{id}") {
            val id = call.parameters["id"] ?: throw NotFound()
            call.respond(graph.node(viewerId(call.firebaseUser()), id))
        }
        get("/graph/fof") {
            call.respond(fof.fof(viewerId(call.firebaseUser())))
        }
        get("/graph/history") {
            call.respond(history.history(viewerId(call.firebaseUser())))
        }
        stubGet("/communities")
        stubGet("/connect-requests")
    }

    override fun Route.internal() {
        stubPost("/tasks/name-community")
        stubPost("/jobs/communities")
    }
}

private fun ApplicationCall.firebaseUser(): FirebaseUser = principal<FirebaseUser>() ?: throw Unauthenticated()
