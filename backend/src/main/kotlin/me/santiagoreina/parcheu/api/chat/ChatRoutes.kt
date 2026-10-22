package me.santiagoreina.parcheu.api.chat

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import me.santiagoreina.parcheu.api.identity.UserRecord
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.auth.FirebaseUser
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.Unauthenticated
import me.santiagoreina.parcheu.contract.Mute
import me.santiagoreina.parcheu.contract.SendMessage
import java.util.UUID

/**
 * Rutas del dominio chat (E5.1, arquitectura.md §5.2). [currentUser] traduce el usuario de
 * Firebase a su fila de `users` (lo resuelve identity; la suspensión y los términos ya los revisó
 * `TermsGate`).
 */
class ChatRoutes(
    private val chats: ChatService,
    private val currentUser: suspend (FirebaseUser) -> UserRecord,
) : DomainRoutes {
    override fun Route.v1() {
        get("/chats") {
            call.respond(chats.list(call.user()))
        }
        post("/chats/{id}/messages") {
            val user = call.user()
            call.respond(chats.send(user, call.chatId(), call.receive<SendMessage>()))
        }
        post("/chats/{id}/read") {
            chats.markRead(call.user(), call.chatId())
            call.respond(HttpStatusCode.NoContent)
        }
        post("/chats/{id}/mute") {
            val user = call.user()
            chats.mute(user, call.chatId(), call.receive<Mute>().muted)
            call.respond(HttpStatusCode.NoContent)
        }
    }

    private suspend fun ApplicationCall.user(): UserRecord = currentUser(principal<FirebaseUser>() ?: throw Unauthenticated())
}

/** Un id que no es uuid no puede ser un chat: 404, igual que uno que no existe. */
private fun ApplicationCall.chatId(): UUID =
    parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw NotFound("El chat no existe")
