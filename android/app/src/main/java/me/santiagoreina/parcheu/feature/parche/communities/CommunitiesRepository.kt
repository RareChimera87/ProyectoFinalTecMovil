package me.santiagoreina.parcheu.feature.parche.communities

import me.santiagoreina.parcheu.contract.CommunitiesDto
import me.santiagoreina.parcheu.contract.CommunityDto
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall
import retrofit2.http.GET

/** Ruta de la API v1 que usa D8 (arquitectura.md §5.2). */
interface CommunitiesApi {
    @GET("v1/communities")
    suspend fun communities(): CommunitiesDto
}

/**
 * Lo que D8 necesita. [plus] sale de la respuesta: el servidor solo manda `items` a ParcheU+ (gratis
 * recibe `count`, el conteo bloqueado).
 */
data class CommunitiesContent(val count: Int, val items: List<CommunityDto>, val plus: Boolean)

/** Datos de D8. La UI depende solo de esta interfaz; las pruebas usan `FakeCommunitiesRepository`. */
interface CommunitiesRepository {
    suspend fun load(): ApiResult<CommunitiesContent>
}

class CommunitiesRepositoryImpl(
    private val api: CommunitiesApi,
    private val mapper: ApiErrorMapper
) : CommunitiesRepository {
    override suspend fun load(): ApiResult<CommunitiesContent> =
        when (val result = apiCall(mapper) { api.communities() }) {
            is ApiResult.Failure -> result
            is ApiResult.Success ->
                ApiResult.Success(
                    CommunitiesContent(
                        count = result.value.count,
                        items = result.value.items.orEmpty(),
                        plus = result.value.items != null
                    )
                )
        }
}
