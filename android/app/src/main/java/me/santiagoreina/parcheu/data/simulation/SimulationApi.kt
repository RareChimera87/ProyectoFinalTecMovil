package me.santiagoreina.parcheu.data.simulation

import me.santiagoreina.parcheu.contract.Page
import me.santiagoreina.parcheu.contract.QuotaDto
import me.santiagoreina.parcheu.contract.SimulationDto
import me.santiagoreina.parcheu.contract.SimulationSummary
import me.santiagoreina.parcheu.contract.StartSimulation
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Rutas de simulaciones de la API v1 que usa F4-F6 (arquitectura.md §5.2). */
interface SimulationApi {
    @GET("v1/simulations/{id}")
    suspend fun simulation(@Path("id") id: String): SimulationDto

    @GET("v1/simulations")
    suspend fun simulations(@Query("cursor") cursor: String? = null): Page<SimulationSummary>

    @POST("v1/simulations")
    suspend fun start(@Body body: StartSimulation): SimulationSummary

    @GET("v1/quota")
    suspend fun quota(): QuotaDto
}
