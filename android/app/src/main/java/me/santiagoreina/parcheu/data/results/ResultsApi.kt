package me.santiagoreina.parcheu.data.results

import me.santiagoreina.parcheu.contract.Decision
import me.santiagoreina.parcheu.contract.DecisionResult
import me.santiagoreina.parcheu.contract.QuotaDto
import me.santiagoreina.parcheu.contract.ReportDto
import me.santiagoreina.parcheu.contract.SimulationDto
import me.santiagoreina.parcheu.contract.TranscriptDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Rutas que usan F7/F8 y la decisión mutua (arquitectura.md §5.2, E4.6). */
interface ResultsApi {
    @GET("v1/simulations/{id}")
    suspend fun simulation(@Path("id") id: String): SimulationDto

    @GET("v1/simulations/{id}/report")
    suspend fun report(@Path("id") id: String): ReportDto

    @POST("v1/simulations/{id}/report:unlock")
    suspend fun unlock(@Path("id") id: String): ReportDto

    @POST("v1/simulations/{id}/decision")
    suspend fun decide(@Path("id") id: String, @Body body: Decision): DecisionResult

    @GET("v1/simulations/{id}/transcript")
    suspend fun transcript(@Path("id") id: String): TranscriptDto

    @GET("v1/quota")
    suspend fun quota(): QuotaDto
}
