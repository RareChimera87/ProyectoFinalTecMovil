package me.santiagoreina.parcheu.feature.safety

import me.santiagoreina.parcheu.contract.BlockedDto
import me.santiagoreina.parcheu.contract.Mute
import me.santiagoreina.parcheu.contract.ReportCreated
import me.santiagoreina.parcheu.contract.ReportRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Rutas de reportar, bloquear y silenciar de la API v1 (arquitectura.md §5.2). Nunca responden 402. */
interface SafetyApi {
    @POST("v1/reports")
    suspend fun report(@Body body: ReportRequest): ReportCreated

    @GET("v1/blocks")
    suspend fun blocks(): List<BlockedDto>

    /** [userId] es el uuid de una persona conectada o el `anonId` con el que se la ve. */
    @POST("v1/blocks/{userId}")
    suspend fun block(@Path("userId") userId: String)

    @DELETE("v1/blocks/{userId}")
    suspend fun unblock(@Path("userId") userId: String)

    @POST("v1/chats/{id}/mute")
    suspend fun mute(@Path("id") chatId: String, @Body body: Mute)
}
