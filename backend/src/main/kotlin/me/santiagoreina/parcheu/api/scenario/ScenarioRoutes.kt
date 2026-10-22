package me.santiagoreina.parcheu.api.scenario

import io.ktor.server.routing.Route
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.stubGet
import me.santiagoreina.parcheu.api.platform.stubPost

/** Rutas del dominio scenario: stubs 501 de E0.3; las rellena su feature. */
class ScenarioRoutes : DomainRoutes {
    override fun Route.v1() {
        stubGet("/scenarios")
        stubPost("/scenario-sessions/{chatId}/agreement")
    }
}
