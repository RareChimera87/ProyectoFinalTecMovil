package me.santiagoreina.parcheu.api.identity.profile

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.errors.Unauthenticated
import me.santiagoreina.parcheu.contract.AgeVerification

/**
 * Rutas del perfil básico (B0, E2.6). Las registra `IdentityRoutes` dentro de su ruta `/v1`, así que
 * pasan por la misma puerta de términos y de edad.
 */
internal fun Route.profileRoutes(service: ProfileService) {
    get("/catalog") { call.respond(service.catalog(call.firebaseUser())) }
    post("/me/age-verification") {
        call.respond(service.verifyAge(call.firebaseUser(), call.receive<AgeVerification>()))
    }
}

private fun ApplicationCall.firebaseUser(): FirebaseUser = principal<FirebaseUser>() ?: throw Unauthenticated()
