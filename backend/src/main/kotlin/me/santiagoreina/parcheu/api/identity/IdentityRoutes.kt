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
import io.ktor.server.routing.put
import me.santiagoreina.parcheu.api.identity.deletion.AccountDeletionService
import me.santiagoreina.parcheu.api.identity.deletion.AccountModule
import me.santiagoreina.parcheu.api.identity.modes.ModesService
import me.santiagoreina.parcheu.api.identity.privacy.PrivacyService
import me.santiagoreina.parcheu.api.identity.profile.ProfileService
import me.santiagoreina.parcheu.api.identity.profile.profileRoutes
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.errors.Unauthenticated
import me.santiagoreina.parcheu.api.platform.stubPost
import me.santiagoreina.parcheu.api.twin.TaskPayload
import me.santiagoreina.parcheu.contract.AcceptTerms
import me.santiagoreina.parcheu.contract.ConsentChange
import me.santiagoreina.parcheu.contract.FcmToken
import me.santiagoreina.parcheu.contract.UpdateMe
import me.santiagoreina.parcheu.contract.UpdateModes
import me.santiagoreina.parcheu.contract.UpdatePrivacy

/**
 * Rutas del dominio identity. E2.1 implementa sesión, términos, `GET/PATCH /me`, consentimientos y
 * tokens FCM; E2.5 agrega `PUT /me/modes` ([ModesService]); E2.6, el catálogo y la verificación de
 * edad ([profileRoutes]); E8.3, `DELETE /me`, `GET /me/export` y la tarea `purge-user` ([AccountModule]);
 * E8.2, `GET/PATCH /me/privacy` ([PrivacyService]). El job de retención sigue como stub 501 hasta E8.8.
 */
class IdentityRoutes(
    private val service: IdentityService,
    private val profile: ProfileService,
    /** Borrado y exportación de la cuenta (E8.3). */
    private val account: AccountModule,
    private val modes: ModesService = ModesService(service, service.repo),
    private val privacy: PrivacyService = PrivacyService(service),
) : DomainRoutes {
    override fun Route.v1() {
        // Puerta de términos para todas las rutas /v1 autenticadas (ver TermsGate).
        install(TermsGate) { service = this@IdentityRoutes.service }

        post("/session") { call.respond(service.session(call.firebaseUser())) }
        post("/me/terms") { call.respond(service.acceptTerms(call.firebaseUser(), call.receive<AcceptTerms>())) }
        get("/me") { call.respond(service.me(call.firebaseUser())) }
        patch("/me") { call.respond(service.updateMe(call.firebaseUser(), call.receive<UpdateMe>())) }
        post("/me/consent") { call.respond(service.changeConsent(call.firebaseUser(), call.receive<ConsentChange>())) }
        put("/me/modes") { call.respond(modes.update(call.firebaseUser(), call.receive<UpdateModes>())) }
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

        // E8.3: borrado lógico inmediato + purga asíncrona; exportación en JSON (Ley 1581).
        delete("/me") {
            account.deletion.requestDeletion(call.firebaseUser())
            call.respond(HttpStatusCode.Accepted)
        }
        get("/me/export") { call.respond(account.export.export(call.firebaseUser())) }

        // E8.2: visibilidad en el grafo y aprendizaje de chats por modo (H3).
        get("/me/privacy") { call.respond(privacy.get(call.firebaseUser())) }
        patch("/me/privacy") { call.respond(privacy.update(call.firebaseUser(), call.receive<UpdatePrivacy>())) }
    }

    override fun Route.internal() {
        post(AccountDeletionService.PURGE_PATH) {
            account.deletion.purge(call.receive<TaskPayload>().uuid())
            call.respond(HttpStatusCode.NoContent)
        }
        // Reencola las purgas que agotaron sus reintentos (Cloud Scheduler, cada hora).
        post(AccountDeletionService.SWEEP_PATH) {
            account.deletion.sweepStalled()
            call.respond(HttpStatusCode.NoContent)
        }

        stubPost("/jobs/retention")
    }
}

private fun ApplicationCall.firebaseUser(): FirebaseUser = principal<FirebaseUser>() ?: throw Unauthenticated()
