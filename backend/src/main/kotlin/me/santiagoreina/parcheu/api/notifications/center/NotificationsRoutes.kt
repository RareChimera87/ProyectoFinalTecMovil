package me.santiagoreina.parcheu.api.notifications.center

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.errors.Unauthenticated
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.MarkRead
import java.util.UUID

/**
 * Rutas del centro de notificaciones C2 (E5.4): `GET /v1/notifications` y `POST /v1/notifications/read`.
 * [viewerId] traduce el usuario de Firebase a `users.id` (lo resuelve identity).
 */
class NotificationsRoutes(
    private val center: NotificationCenterService,
    private val viewerId: suspend (FirebaseUser) -> UUID,
) : DomainRoutes {
    override fun Route.v1() {
        get("/notifications") {
            val user = call.principal<FirebaseUser>() ?: throw Unauthenticated()
            call.respond(center.list(viewerId(user)))
        }
        post("/notifications/read") {
            val user = call.principal<FirebaseUser>() ?: throw Unauthenticated()
            // Sin cuerpo equivale a `{}` (marcar todas); un cuerpo mal formado es 400, nunca "todas".
            val raw = call.receiveText()
            val body = if (raw.isBlank()) MarkRead() else ContractJson.decodeFromString(MarkRead.serializer(), raw)
            center.markRead(viewerId(user), body)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
