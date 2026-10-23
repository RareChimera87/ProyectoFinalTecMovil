package me.santiagoreina.parcheu.api.chat

import io.ktor.server.routing.Route
import me.santiagoreina.parcheu.api.platform.DomainRoutes
import me.santiagoreina.parcheu.api.platform.stubGet
import me.santiagoreina.parcheu.api.platform.stubPost

/** Rutas del dominio chat: stubs 501 de E0.3; las rellena su feature. */
class ChatRoutes : DomainRoutes {
    override fun Route.v1() {
        stubGet("/chats")
        stubPost("/chats/{id}/messages")
        stubPost("/chats/{id}/read")
        stubPost("/chats/{id}/mute")
    }
}
