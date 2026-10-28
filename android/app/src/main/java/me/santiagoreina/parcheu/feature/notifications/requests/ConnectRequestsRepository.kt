package me.santiagoreina.parcheu.feature.notifications.requests

import me.santiagoreina.parcheu.contract.ConnectRequestsDto
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall
import retrofit2.http.GET

/** 'Quién quiere conectar contigo' (arquitectura.md §5.2). */
interface ConnectRequestsApi {
    /** Gratis recibe solo `count`; ParcheU+ también `items`. */
    @GET("v1/connect-requests")
    suspend fun requests(): ConnectRequestsDto
}

/** Lo que usa la sección de C2 (E6.6). */
interface ConnectRequestsRepository {
    suspend fun requests(): ApiResult<ConnectRequestsDto>
}

class RemoteConnectRequestsRepository(
    private val api: ConnectRequestsApi,
    private val mapper: ApiErrorMapper
) : ConnectRequestsRepository {
    override suspend fun requests() = apiCall(mapper) { api.requests() }
}
