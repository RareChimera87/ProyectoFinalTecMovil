package me.santiagoreina.parcheu.api.billing

import io.ktor.server.routing.Route
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.stubGet
import me.santiagoreina.parcheu.api.platform.stubPost

/** Rutas del dominio billing: stubs 501 de E0.3; las rellena su feature. */
class BillingRoutes : DomainRoutes {
    override fun Route.v1() {
        stubGet("/billing/entitlement")
        stubPost("/billing/verify")
    }

    override fun Route.internal() {
        stubPost("/billing/rtdn")
        stubPost("/dev/grant-plus")
    }
}
