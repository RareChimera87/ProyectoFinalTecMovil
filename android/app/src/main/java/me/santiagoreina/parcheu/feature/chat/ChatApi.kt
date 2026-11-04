package me.santiagoreina.parcheu.feature.chat

import me.santiagoreina.parcheu.contract.ChatSummary
import me.santiagoreina.parcheu.contract.MessageDto
import me.santiagoreina.parcheu.contract.Mute
import me.santiagoreina.parcheu.contract.SendMessage
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Rutas de chat de la API v1 (arquitectura.md §5.2). Los mensajes se leen por Firestore, no por aquí. */
interface ChatApi {
    @GET("v1/chats")
    suspend fun chats(): List<ChatSummary>

    /** Idempotente por `clientId`: repetir el envío devuelve el mismo mensaje. */
    @POST("v1/chats/{id}/messages")
    suspend fun send(@Path("id") chatId: String, @Body body: SendMessage): MessageDto

    @POST("v1/chats/{id}/read")
    suspend fun markRead(@Path("id") chatId: String)

    @POST("v1/chats/{id}/mute")
    suspend fun mute(@Path("id") chatId: String, @Body body: Mute)
}
