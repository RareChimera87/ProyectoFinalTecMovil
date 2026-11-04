package me.santiagoreina.parcheu.data.billing

import me.santiagoreina.parcheu.contract.EntitlementDto
import me.santiagoreina.parcheu.contract.VerifyPurchase
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Rutas de billing de la API v1 (arquitectura.md §5.2 y §9). La base de Retrofit termina en `/`. */
interface BillingApi {
    /** Verifica la compra con Play, la reconoce en el servidor y devuelve el derecho vigente. */
    @POST("v1/billing/verify")
    suspend fun verify(@Body body: VerifyPurchase): EntitlementDto

    /** Derecho vigente del usuario (fuente de verdad de Plus, ADR-0013). */
    @GET("v1/billing/entitlement")
    suspend fun entitlement(): EntitlementDto
}
