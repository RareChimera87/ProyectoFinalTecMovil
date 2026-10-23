package me.santiagoreina.parcheu.api.quota

import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.Unauthenticated
import java.time.Clock
import java.util.UUID

/**
 * Lo que el dominio de cuotas expone al resto del backend: [service] para cobrar y devolver,
 * [users] para resolver el usuario del token y [devPlus] (`grant-plus`) solo fuera de prod.
 */
class QuotaModule(
    val service: QuotaService,
    val users: QuotaUserLookup,
    /** Nulo en prod: entonces `POST /internal/dev/grant-plus` no existe (404). */
    val devPlus: DevPlusGranter?,
    val clock: Clock,
)

/** `GET /v1/quota` (arquitectura.md §5.2). `POST /internal/dev/grant-plus` lo registra `BillingRoutes` (E7.1) con [QuotaModule.devPlus]. */
class QuotaRoutes(private val quota: QuotaModule) : DomainRoutes {
    override fun Route.v1() {
        get("/quota") {
            call.respond(quota.service.status(currentUserId()))
        }
    }

    private fun RoutingContext.currentUserId(): UUID {
        val user = call.principal<FirebaseUser>() ?: throw Unauthenticated()
        return quota.users.userIdFor(user.uid) ?: throw NotFound("Usuario sin sesión: llama primero a POST /v1/session")
    }
}
