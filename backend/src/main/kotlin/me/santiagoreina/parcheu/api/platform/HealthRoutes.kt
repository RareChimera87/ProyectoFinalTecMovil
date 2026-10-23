package me.santiagoreina.parcheu.api.platform

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import me.santiagoreina.parcheu.contract.Health
import java.time.Clock

/** `GET /v1/health`: pública, sin autenticación (la sonda de uptime y el despliegue la usan). */
fun Route.healthRoutes(version: String, clock: Clock) {
    get("/health") {
        call.respond(Health(version = version, time = clock.nowInstant()))
    }
}
