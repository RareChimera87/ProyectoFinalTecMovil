package me.santiagoreina.parcheu.api.matching

import io.ktor.server.routing.Route
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.stubDelete
import me.santiagoreina.parcheu.api.platform.stubGet
import me.santiagoreina.parcheu.api.platform.stubPost

/** Rutas del dominio matching: stubs 501 de E0.3; las rellena su feature. */
class MatchingRoutes : DomainRoutes {
    override fun Route.v1() {
        stubPost("/match/queue")
        stubGet("/match/queue")
        stubDelete("/match/queue")
        stubGet("/matches/{id}")
        stubPost("/matches/{id}/choice")
    }
}
