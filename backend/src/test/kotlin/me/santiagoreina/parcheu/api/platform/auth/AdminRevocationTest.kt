package me.santiagoreina.parcheu.api.platform.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

/** B-B2: el token de un admin se verifica con revocación; el de los demás no paga esa llamada. */
class AdminRevocationTest : FunSpec({
    /** Doble: "admin-revocado" es admin con firma válida pero revocado; la revocación solo se ve con `checkRevoked`. */
    class Spy : TokenVerifier {
        var revokedChecks = 0

        override suspend fun verify(token: String): FirebaseUser? = when (token) {
            "admin-revocado", "admin-vigente" -> FirebaseUser("adm", null, null, admin = true)
            "ana" -> FirebaseUser("ana", null, null)
            else -> null
        }

        override suspend fun verify(token: String, checkRevoked: Boolean): FirebaseUser? {
            if (checkRevoked) revokedChecks++
            return if (checkRevoked && token == "admin-revocado") null else verify(token)
        }
    }

    fun withApp(spy: Spy, block: suspend (suspend (String) -> HttpStatusCode) -> Unit) = testApplication {
        application {
            installAuth(spy, FakeOidcVerifier("tasks@test"))
            routing { authenticate(AuthProviders.FIREBASE) { get("/ping") { call.respondText("ok") } } }
        }
        block { token -> client.get("/ping") { header(HttpHeaders.Authorization, "Bearer $token") }.status }
    }

    test("un admin revocado recibe 401; uno vigente pasa") {
        val spy = Spy()
        withApp(spy) { call ->
            call("admin-revocado") shouldBe HttpStatusCode.Unauthorized
            call("admin-vigente") shouldBe HttpStatusCode.OK
        }
    }

    test("un usuario normal no dispara la comprobación de revocación") {
        val spy = Spy()
        withApp(spy) { call -> call("ana") shouldBe HttpStatusCode.OK }
        spy.revokedChecks shouldBe 0
    }
})
