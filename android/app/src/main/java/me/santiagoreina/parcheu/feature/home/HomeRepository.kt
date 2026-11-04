package me.santiagoreina.parcheu.feature.home

import me.santiagoreina.parcheu.contract.HomeDto
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall
import retrofit2.http.GET

/** `GET /v1/home` (arquitectura.md §5.2): cupos, simulaciones, sugerencias y no leídas en una llamada. */
interface HomeApi {
    @GET("v1/home")
    suspend fun home(): HomeDto
}

/** Lo que lee C1. Iniciar una simulación desde una sugerencia usa el repositorio de simulaciones (E4.5). */
interface HomeRepository {
    suspend fun home(): ApiResult<HomeDto>
}

class RemoteHomeRepository(private val api: HomeApi, private val mapper: ApiErrorMapper) :
    HomeRepository {
    override suspend fun home() = apiCall(mapper) { api.home() }
}
