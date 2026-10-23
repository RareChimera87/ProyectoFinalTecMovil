package me.santiagoreina.parcheu.api.safety

import io.ktor.server.routing.Route
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.stubDelete
import me.santiagoreina.parcheu.api.platform.stubGet
import me.santiagoreina.parcheu.api.platform.stubPost

/** Rutas del dominio safety: stubs 501 de E0.3; las rellena su feature. */
class SafetyRoutes : DomainRoutes {
    override fun Route.v1() {
        stubPost("/reports")
        stubGet("/blocks")
        stubPost("/blocks/{userId}")
        stubDelete("/blocks/{userId}")
    }

    override fun Route.internal() {
        stubPost("/tasks/moderate")
    }
}
