package me.santiagoreina.parcheu.api.quota

import io.ktor.server.routing.Route
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.stubGet

/** Rutas del dominio quota: stubs 501 de E0.3; las rellena su feature. */
class QuotaRoutes : DomainRoutes {
    override fun Route.v1() {
        stubGet("/quota")
    }
}
