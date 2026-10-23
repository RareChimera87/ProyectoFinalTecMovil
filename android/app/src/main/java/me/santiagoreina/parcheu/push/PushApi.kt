package me.santiagoreina.parcheu.push

import me.santiagoreina.parcheu.contract.FcmToken
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.HTTP
import retrofit2.http.POST

/** `POST/DELETE /v1/me/fcm-token` (E2.1). */
interface PushApi {
    @POST("v1/me/fcm-token")
    suspend fun register(@Body token: FcmToken)

    // DELETE con cuerpo: Retrofit exige @HTTP para eso.
    @HTTP(method = "DELETE", path = "v1/me/fcm-token", hasBody = true)
    suspend fun unregister(@Body token: FcmToken)
}
