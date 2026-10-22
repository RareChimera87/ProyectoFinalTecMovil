package me.santiagoreina.parcheu.api.simulation

import io.ktor.server.routing.Route
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.stubGet
import me.santiagoreina.parcheu.api.platform.stubPost

/** Rutas del dominio simulation: stubs 501 de E0.3; las rellena su feature. */
class SimulationRoutes : DomainRoutes {
    override fun Route.v1() {
        stubPost("/simulations")
        stubGet("/simulations")
        stubGet("/simulations/{id}")
        stubGet("/simulations/{id}/report")
        stubPost("/simulations/{id}/report:unlock")
        stubGet("/simulations/{id}/transcript")
        stubPost("/simulations/{id}/decision")
    }

    override fun Route.internal() {
        stubPost("/tasks/simulate")
        stubPost("/tasks/judge-scenario")
    }
}
