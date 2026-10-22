package me.santiagoreina.parcheu.feature.basicprofile

import me.santiagoreina.parcheu.contract.AgeVerification
import me.santiagoreina.parcheu.contract.CatalogDto
import me.santiagoreina.parcheu.contract.IsoDate
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.UpdateMe
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall

/** Datos de B0: catálogo, cuenta, verificación de edad y `PATCH /v1/me`. */
interface BasicProfileRepository {
    suspend fun catalog(): ApiResult<CatalogDto>

    suspend fun me(): ApiResult<MeDto>

    suspend fun verifyAge(birthDate: IsoDate): ApiResult<MeDto>

    suspend fun update(request: UpdateMe): ApiResult<MeDto>
}

class BasicProfileRepositoryImpl(private val api: ProfileApi, private val mapper: ApiErrorMapper) :
    BasicProfileRepository {
    override suspend fun catalog() = apiCall(mapper) { api.catalog() }

    override suspend fun me() = apiCall(mapper) { api.me() }

    override suspend fun verifyAge(birthDate: IsoDate) = apiCall(mapper) { api.verifyAge(AgeVerification(birthDate)) }

    override suspend fun update(request: UpdateMe) = apiCall(mapper) { api.updateMe(request) }
}
