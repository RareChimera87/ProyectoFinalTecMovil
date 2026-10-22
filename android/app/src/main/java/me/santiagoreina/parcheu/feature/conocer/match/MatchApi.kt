package me.santiagoreina.parcheu.feature.conocer.match

import me.santiagoreina.parcheu.contract.Choice
import me.santiagoreina.parcheu.contract.Enqueue
import me.santiagoreina.parcheu.contract.MatchDto
import me.santiagoreina.parcheu.contract.TicketDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Rutas de la fila de match y de la elección tras el match (arquitectura.md §5.2 y §7.4-7.5). */
interface MatchApi {
    /** Entra a la fila; puede responder ya `MATCHED` si había alguien esperando. */
    @POST("v1/match/queue")
    suspend fun enqueue(@Body body: Enqueue): TicketDto

    /** Sale de la fila (204). Cancelar antes del match no cobra nada. */
    @DELETE("v1/match/queue")
    suspend fun cancel()

    @GET("v1/matches/{id}")
    suspend fun match(@Path("id") id: String): MatchDto

    @POST("v1/matches/{id}/choice")
    suspend fun choose(@Path("id") id: String, @Body body: Choice): MatchDto
}
