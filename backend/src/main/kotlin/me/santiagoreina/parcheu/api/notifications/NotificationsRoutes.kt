package me.santiagoreina.parcheu.api.notifications

import io.ktor.server.routing.Route
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.stubGet
import me.santiagoreina.parcheu.api.platform.stubPost

/** Rutas del dominio notifications: stubs 501 de E0.3; las rellena su feature. */
class NotificationsRoutes : DomainRoutes {
    override fun Route.v1() {
        stubGet("/notifications")
        stubPost("/notifications/read")
    }
}
