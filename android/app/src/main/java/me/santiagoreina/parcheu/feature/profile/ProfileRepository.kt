package me.santiagoreina.parcheu.feature.profile

import me.santiagoreina.parcheu.contract.CatalogDto
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.TwinDto
import me.santiagoreina.parcheu.contract.TwinPoint
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall
import me.santiagoreina.parcheu.feature.basicprofile.ProfileApi
import retrofit2.http.GET

/** Rutas del gemelo que lee C3 (arquitectura.md §5.2). La base de Retrofit termina en `/`. */
interface TwinProfileApi {
    @GET("v1/twin")
    suspend fun twin(): TwinDto

    /** Un punto por semana con datos, en orden ascendente. */
    @GET("v1/twin/evolution")
    suspend fun evolution(): List<TwinPoint>
}

/** Lo que lee C3: cuenta, catálogo (nombre de la universidad), gemelo y evolución semanal. */
interface ProfileRepository {
    suspend fun me(): ApiResult<MeDto>

    suspend fun catalog(): ApiResult<CatalogDto>

    suspend fun twin(): ApiResult<TwinDto>

    suspend fun evolution(): ApiResult<List<TwinPoint>>
}

class RemoteProfileRepository(
    private val profileApi: ProfileApi,
    private val twinApi: TwinProfileApi,
    private val mapper: ApiErrorMapper
) : ProfileRepository {
    override suspend fun me() = apiCall(mapper) { profileApi.me() }

    override suspend fun catalog() = apiCall(mapper) { profileApi.catalog() }

    override suspend fun twin() = apiCall(mapper) { twinApi.twin() }

    override suspend fun evolution() = apiCall(mapper) { twinApi.evolution() }
}
