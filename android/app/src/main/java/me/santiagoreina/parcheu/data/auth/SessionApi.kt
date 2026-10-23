package me.santiagoreina.parcheu.data.auth

import me.santiagoreina.parcheu.contract.AcceptTerms
import me.santiagoreina.parcheu.contract.ConsentsDto
import me.santiagoreina.parcheu.contract.SessionDto
import retrofit2.http.Body
import retrofit2.http.POST

/** Rutas de sesión de la API v1 (arquitectura.md §5.2). La base de Retrofit termina en `/`. */
interface SessionApi {
    /** Crea o actualiza al usuario a partir del ID token y devuelve el paso de onboarding. */
    @POST("v1/session")
    suspend fun session(): SessionDto

    /** Acepta Términos y Política en [AcceptTerms.textVersion] (agrega filas TERMS y PRIVACY). */
    @POST("v1/me/terms")
    suspend fun acceptTerms(@Body body: AcceptTerms): ConsentsDto
}
