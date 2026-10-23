package me.santiagoreina.parcheu.api.home

import io.ktor.server.routing.Route
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.stubGet

/** Rutas del dominio home: stubs 501 de E0.3; las rellena su feature. */
class HomeRoutes : DomainRoutes {
    override fun Route.v1() {
        stubGet("/home")
    }
}
