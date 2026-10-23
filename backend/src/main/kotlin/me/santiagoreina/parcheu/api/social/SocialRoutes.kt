package me.santiagoreina.parcheu.api.social

import io.ktor.server.routing.Route
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.stubGet
import me.santiagoreina.parcheu.api.platform.stubPost

/** Rutas del dominio social: stubs 501 de E0.3; las rellena su feature. */
class SocialRoutes : DomainRoutes {
    override fun Route.v1() {
        stubPost("/introductions")
        stubGet("/introductions/{id}")
        stubPost("/introductions/{id}/response")
        stubPost("/introductions/request")
        stubGet("/invites/me")
        stubPost("/invites/redeem")
        stubPost("/invites/{userId}/accept")
    }
}
