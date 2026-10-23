package me.santiagoreina.parcheu.api.social

import io.ktor.server.routing.Route
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.stubGet
import me.santiagoreina.parcheu.api.platform.stubPost

/**
 * Rutas del dominio social: invitaciones (stubs 501 de E0.3 hasta E6.1). Las presentaciones viven en
 * `introductions/IntroductionRoutes` (E6.2).
 */
class SocialRoutes : DomainRoutes {
    override fun Route.v1() {
        stubGet("/invites/me")
        stubPost("/invites/redeem")
        stubPost("/invites/{userId}/accept")
    }
}
