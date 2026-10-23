package me.santiagoreina.parcheu.api.home

import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.errors.Unauthenticated
import java.util.UUID

/**
 * Rutas del dominio home: `GET /v1/home` (C1, E4.7). [viewerId] traduce el usuario de Firebase a
 * `users.id` (lo resuelve identity).
 */
class HomeRoutes(
    private val home: HomeService,
    private val viewerId: suspend (FirebaseUser) -> UUID,
) : DomainRoutes {
    override fun Route.v1() {
        get("/home") {
            val user = call.principal<FirebaseUser>() ?: throw Unauthenticated()
            call.respond(home.home(viewerId(user)))
        }
    }
}
