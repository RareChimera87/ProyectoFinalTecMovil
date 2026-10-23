package me.santiagoreina.parcheu.api.billing

import io.ktor.server.routing.Route
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.stubGet
import me.santiagoreina.parcheu.api.platform.stubPost

/**
 * Rutas del dominio billing: stubs 501 de E0.3; las rellena su feature. `POST /internal/dev/grant-plus`
 * lo registra `QuotaRoutes` (E4.1) solo fuera de prod.
 */
class BillingRoutes : DomainRoutes {
    override fun Route.v1() {
        stubGet("/billing/entitlement")
        stubPost("/billing/verify")
    }

    override fun Route.internal() {
        stubPost("/billing/rtdn")
    }
}
