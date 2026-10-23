package me.santiagoreina.parcheu.api.billing

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.errors.Unauthenticated
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.api.quota.DevPlusGranter
import me.santiagoreina.parcheu.contract.GrantPlus
import me.santiagoreina.parcheu.contract.VerifyPurchase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.UUID

/**
 * Rutas del dominio billing (arquitectura.md §5.2 y §9):
 * - `GET /v1/billing/entitlement` y `POST /v1/billing/verify` (ID token de Firebase).
 * - `POST /internal/billing/rtdn` (push de Pub/Sub con OIDC de `sa-tasks`).
 * - `POST /internal/dev/grant-plus` solo si hay [devPlus]: el contenedor lo pasa nulo con `ENV=prod`, así
 *   que en prod la ruta no existe (404).
 */
class BillingRoutes(
    private val billing: BillingService,
    private val devPlus: DevPlusGranter?,
    private val clock: Clock,
    private val viewerId: suspend (FirebaseUser) -> UUID,
) : DomainRoutes {
    override fun Route.v1() {
        get("/billing/entitlement") {
            val user = call.principal<FirebaseUser>() ?: throw Unauthenticated()
            val userId = viewerId(user)
            call.respond(io { billing.entitlement(userId) })
        }
        post("/billing/verify") {
            val user = call.principal<FirebaseUser>() ?: throw Unauthenticated()
            val body = call.receive<VerifyPurchase>()
            val userId = viewerId(user)
            call.respond(io { billing.verify(userId, body) })
        }
    }

    override fun Route.internal() {
        post("/billing/rtdn") {
            val rtdn = try {
                decodeRtdn(call.receiveText())
            } catch (e: IllegalArgumentException) {
                throw ValidationFailed(e.message ?: "Push de Pub/Sub inválido")
            }
            io { billing.handleRtdn(rtdn) }
            // 204 también para repetidas e ignoradas: así Pub/Sub no las reintenta.
            call.respond(HttpStatusCode.NoContent)
        }
        val granter = devPlus ?: return
        post("/dev/grant-plus") {
            val body = call.receive<GrantPlus>()
            val userId = runCatching { UUID.fromString(body.userId) }.getOrElse { throw ValidationFailed("userId no es un UUID") }
            io { granter.grantPlus(userId, body.days, clock.instant()) }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

/**
 * Billing llama a Play (red, hasta 15 s) y a la base de forma bloqueante: se pasa a `Dispatchers.IO` para no
 * ocupar el hilo de llamadas de Netty (en Cloud Run con 1 CPU es uno solo y bloquearía toda la API).
 */
private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
