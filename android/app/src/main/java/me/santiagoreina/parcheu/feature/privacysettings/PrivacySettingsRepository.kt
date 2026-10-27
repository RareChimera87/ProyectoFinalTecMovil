package me.santiagoreina.parcheu.feature.privacysettings

import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.PrivacyDto
import me.santiagoreina.parcheu.contract.UpdatePrivacy
import me.santiagoreina.parcheu.contract.Visibility
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

/** Rutas de privacidad de la API v1 (arquitectura.md §5.2). */
interface PrivacyApi {
    @GET("v1/me/privacy")
    suspend fun privacy(): PrivacyDto

    @PATCH("v1/me/privacy")
    suspend fun update(@Body body: UpdatePrivacy): PrivacyDto
}

/** Ajustes de privacidad (H3). Los modos se cambian con `ModesRepository`; las pruebas usan un fake. */
interface PrivacySettingsRepository {
    /** `GET /v1/me/privacy`: visibilidad, modos y aprendizaje por modo. */
    suspend fun privacy(): ApiResult<PrivacyDto>

    /** `PATCH /v1/me/privacy`: solo lo que no es nulo. Apagar un aprendizaje borra las memorias pendientes. */
    suspend fun update(
        visibility: Visibility? = null,
        learning: Map<Mode, Boolean>? = null
    ): ApiResult<PrivacyDto>
}

class ApiPrivacySettingsRepository(
    private val api: PrivacyApi,
    private val errorMapper: ApiErrorMapper
) : PrivacySettingsRepository {
    override suspend fun privacy(): ApiResult<PrivacyDto> = apiCall(errorMapper) { api.privacy() }

    override suspend fun update(
        visibility: Visibility?,
        learning: Map<Mode, Boolean>?
    ): ApiResult<PrivacyDto> =
        apiCall(errorMapper) { api.update(UpdatePrivacy(visibility, learning)) }
}
