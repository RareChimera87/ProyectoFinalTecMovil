package me.santiagoreina.parcheu.feature.invites

import me.santiagoreina.parcheu.contract.InviteDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Redeem
import me.santiagoreina.parcheu.contract.RedeemResult
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Invitaciones por QR y código en la API v1 (arquitectura.md §5.2, E6.1). */
interface InvitesApi {
    @GET("v1/invites/me")
    suspend fun me(): InviteDto

    @POST("v1/invites/redeem")
    suspend fun redeem(@Body body: Redeem): RedeemResult

    /**
     * Acepta UN canje: [id] es el `PendingInvite.id` (`anonId:MODO`) y [mode] su modo. Solo ese modo
     * queda CONNECTED; los demás canjes de la misma persona siguen pendientes.
     */
    @POST("v1/invites/{id}/accept")
    suspend fun accept(@Path("id") id: String, @Query("mode") mode: Mode): RedeemResult
}
