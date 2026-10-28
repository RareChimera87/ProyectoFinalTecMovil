package me.santiagoreina.parcheu.feature.conocer.scenario

import me.santiagoreina.parcheu.contract.ScenarioSessionDto
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Rutas del modo escenario (arquitectura.md §5.2 y §7.6, E4.8). */
interface ScenarioApi {
    @GET("v1/scenario-sessions/{chatId}")
    suspend fun session(@Path("chatId") chatId: String): ScenarioSessionDto

    /** Marca 'Llegamos a un acuerdo'; 409 `SCENARIO_ENDED` si el tiempo ya venció. */
    @POST("v1/scenario-sessions/{chatId}/agreement")
    suspend fun agree(@Path("chatId") chatId: String): ScenarioSessionDto
}
