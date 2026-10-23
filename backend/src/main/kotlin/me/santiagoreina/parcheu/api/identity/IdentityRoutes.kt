package me.santiagoreina.parcheu.api.identity

import io.ktor.server.routing.Route
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.stubDelete
import me.santiagoreina.parcheu.api.platform.stubGet
import me.santiagoreina.parcheu.api.platform.stubPatch
import me.santiagoreina.parcheu.api.platform.stubPost
import me.santiagoreina.parcheu.api.platform.stubPut

/** Rutas del dominio identity: stubs 501 de E0.3; las rellena su feature. */
class IdentityRoutes : DomainRoutes {
    override fun Route.v1() {
        stubPost("/session")
        stubPost("/me/terms")
        stubGet("/catalog")
        stubGet("/me")
        stubPatch("/me")
        stubDelete("/me")
        stubGet("/me/export")
        stubPut("/me/modes")
        stubPost("/me/age-verification")
        stubPost("/me/consent")
        stubGet("/me/consents")
        stubGet("/me/privacy")
        stubPatch("/me/privacy")
        stubPost("/me/fcm-token")
        stubDelete("/me/fcm-token")
    }

    override fun Route.internal() {
        stubPost("/tasks/purge-user")
        stubPost("/jobs/retention")
    }
}
