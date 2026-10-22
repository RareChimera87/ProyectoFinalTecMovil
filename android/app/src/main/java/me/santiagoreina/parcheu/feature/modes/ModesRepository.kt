package me.santiagoreina.parcheu.feature.modes

import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.UpdateModes
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

/** Rutas de modos de la API v1 (arquitectura.md §5.2). */
interface ModesApi {
    @GET("v1/me")
    suspend fun me(): MeDto

    /** Reemplaza el estado de los tres modos; Pareja exige edad verificada y [UpdateModes.parejaConfirmed]. */
    @PUT("v1/me/modes")
    suspend fun updateModes(@Body body: UpdateModes): MeDto
}

/** Modos del usuario (B2, B3, B5 y H3). Las pruebas usan un fake. */
interface ModesRepository {
    /** `GET /v1/me`: modos actuales, fecha de nacimiento verificada y confirmación de Pareja. */
    suspend fun me(): ApiResult<MeDto>

    /**
     * `PUT /v1/me/modes`. [parejaConfirmed] es la casilla de B3 marcada en esta sesión: el servidor la
     * exige solo al encender Pareja.
     */
    suspend fun updateModes(modes: Map<Mode, Boolean>, parejaConfirmed: Boolean): ApiResult<MeDto>
}

class ApiModesRepository(private val api: ModesApi, private val errorMapper: ApiErrorMapper) : ModesRepository {
    override suspend fun me(): ApiResult<MeDto> = apiCall(errorMapper) { api.me() }

    override suspend fun updateModes(modes: Map<Mode, Boolean>, parejaConfirmed: Boolean): ApiResult<MeDto> =
        apiCall(errorMapper) { api.updateModes(UpdateModes(modes, parejaConfirmed)) }
}
