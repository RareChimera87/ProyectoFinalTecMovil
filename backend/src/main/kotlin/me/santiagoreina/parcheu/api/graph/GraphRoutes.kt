package me.santiagoreina.parcheu.api.graph

import io.ktor.server.routing.Route
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.stubGet
import me.santiagoreina.parcheu.api.platform.stubPost

/** Rutas del dominio graph: stubs 501 de E0.3; las rellena su feature. */
class GraphRoutes : DomainRoutes {
    override fun Route.v1() {
        stubGet("/graph")
        stubGet("/graph/nodes/{id}")
        stubGet("/graph/fof")
        stubGet("/graph/history")
        stubGet("/communities")
        stubGet("/connect-requests")
    }

    override fun Route.internal() {
        stubPost("/tasks/name-community")
        stubPost("/jobs/communities")
    }
}
