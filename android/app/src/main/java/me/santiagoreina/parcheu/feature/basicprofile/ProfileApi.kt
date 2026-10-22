package me.santiagoreina.parcheu.feature.basicprofile

import me.santiagoreina.parcheu.contract.AgeVerification
import me.santiagoreina.parcheu.contract.CatalogDto
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.UpdateMe
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST

/** Rutas de B0 (E2.6, arquitectura.md §5.2). La base de Retrofit termina en `/`. */
interface ProfileApi {
    @GET("v1/catalog")
    suspend fun catalog(): CatalogDto

    @GET("v1/me")
    suspend fun me(): MeDto

    /** Fecha de nacimiento (inmutable): 403 UNDERAGE si es menor de 18, 409 CONFLICT si ya había otra. */
    @POST("v1/me/age-verification")
    suspend fun verifyAge(@Body body: AgeVerification): MeDto

    @PATCH("v1/me")
    suspend fun updateMe(@Body body: UpdateMe): MeDto
}
