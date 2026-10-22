package me.santiagoreina.parcheu.api.identity

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import me.santiagoreina.parcheu.api.identity.profile.ProfileService
import me.santiagoreina.parcheu.api.identity.profile.profileRoutes
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.errors.Unauthenticated
import me.santiagoreina.parcheu.api.platform.stubDelete
import me.santiagoreina.parcheu.api.platform.stubGet
import me.santiagoreina.parcheu.api.platform.stubPatch
import me.santiagoreina.parcheu.api.platform.stubPost
import me.santiagoreina.parcheu.api.platform.stubPut
import me.santiagoreina.parcheu.contract.AcceptTerms
import me.santiagoreina.parcheu.contract.ConsentChange
import me.santiagoreina.parcheu.contract.FcmToken
import me.santiagoreina.parcheu.contract.UpdateMe

/**
 * Rutas del dominio identity. E2.1 implementa sesión, términos, `GET/PATCH /me`, consentimientos y
 * tokens FCM; E2.6, el catálogo y la verificación de edad ([profileRoutes]); el resto sigue como stub
 * 501 hasta su feature (modos y privacidad: E2.5/E8.x; `DELETE /me` y exportación: E8.x).
 */
class IdentityRoutes(private val service: IdentityService, private val profile: ProfileService) : DomainRoutes {
    override fun Route.v1() {
        // Puerta de términos para todas las rutas /v1 autenticadas (ver TermsGate).
        install(TermsGate) { service = this@IdentityRoutes.service }

        post("/session") { call.respond(service.session(call.firebaseUser())) }
        post("/me/terms") { call.respond(service.acceptTerms(call.firebaseUser(), call.receive<AcceptTerms>())) }
        get("/me") { call.respond(service.me(call.firebaseUser())) }
        patch("/me") { call.respond(service.updateMe(call.firebaseUser(), call.receive<UpdateMe>())) }
        post("/me/consent") { call.respond(service.changeConsent(call.firebaseUser(), call.receive<ConsentChange>())) }
        get("/me/consents") { call.respond(service.consents(call.firebaseUser())) }
        post("/me/fcm-token") {
            service.registerFcmToken(call.firebaseUser(), call.receive<FcmToken>())
            call.respond(HttpStatusCode.NoContent)
        }
        delete("/me/fcm-token") {
            service.removeFcmToken(call.firebaseUser(), call.receive<FcmToken>())
            call.respond(HttpStatusCode.NoContent)
        }

        profileRoutes(profile)

        stubDelete("/me")
        stubGet("/me/export")
        stubPut("/me/modes")
        stubGet("/me/privacy")
        stubPatch("/me/privacy")
    }

    override fun Route.internal() {
        stubPost("/tasks/purge-user")
        stubPost("/jobs/retention")
    }
}

private fun ApplicationCall.firebaseUser(): FirebaseUser = principal<FirebaseUser>() ?: throw Unauthenticated()
