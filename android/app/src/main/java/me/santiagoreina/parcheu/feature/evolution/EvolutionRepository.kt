package me.santiagoreina.parcheu.feature.evolution

import me.santiagoreina.parcheu.contract.HistoryDto
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall
import retrofit2.http.GET

/** Ruta de la API v1 que usa D9 (arquitectura.md §6.4). */
interface EvolutionApi {
    /** Solo ParcheU+: gratis recibe 402 PLUS_REQUIRED. */
    @GET("v1/graph/history")
    suspend fun history(): HistoryDto
}

/** Datos de D9. La UI depende solo de esta interfaz; las pruebas usan un repositorio en memoria. */
interface EvolutionRepository {
    suspend fun history(): ApiResult<HistoryDto>
}

class EvolutionRepositoryImpl(private val api: EvolutionApi, private val mapper: ApiErrorMapper) :
    EvolutionRepository {
    // Sin ParcheU+ D9 muestra su estado bloqueado con su propio botón: el 402 no abre G1 solo.
    override suspend fun history(): ApiResult<HistoryDto> =
        apiCall(mapper, routesPremium = false) { api.history() }
}
