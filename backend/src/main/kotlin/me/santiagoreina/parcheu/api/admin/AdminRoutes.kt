package me.santiagoreina.parcheu.api.admin

import io.ktor.server.routing.Route
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.stubGet
import me.santiagoreina.parcheu.api.platform.stubPost

/** Rutas del dominio admin: stubs 501 de E0.3; las rellena su feature. */
class AdminRoutes : DomainRoutes {
    override fun Route.v1() {
        stubGet("/admin/reports")
        stubPost("/admin/reports/{id}/resolve")
    }
}
