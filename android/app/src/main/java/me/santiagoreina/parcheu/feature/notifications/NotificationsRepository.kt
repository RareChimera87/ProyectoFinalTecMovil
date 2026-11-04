package me.santiagoreina.parcheu.feature.notifications

import me.santiagoreina.parcheu.contract.MarkRead
import me.santiagoreina.parcheu.contract.NotificationsDto
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Centro de notificaciones (arquitectura.md §5.2): bandeja agrupada y 'Marcar leídas'. */
interface NotificationsApi {
    @GET("v1/notifications")
    suspend fun inbox(): NotificationsDto

    /** 204; `ids` nulo o vacío marca todas. */
    @POST("v1/notifications/read")
    suspend fun markRead(@Body body: MarkRead)
}

/** Lo que usa C2 (E5.4). */
interface NotificationsRepository {
    suspend fun inbox(): ApiResult<NotificationsDto>

    /** Marca [ids] como leídas; `null` = todas. */
    suspend fun markRead(ids: List<String>?): ApiResult<Unit>
}

class RemoteNotificationsRepository(
    private val api: NotificationsApi,
    private val mapper: ApiErrorMapper
) : NotificationsRepository {
    override suspend fun inbox() = apiCall(mapper) { api.inbox() }

    override suspend fun markRead(ids: List<String>?) =
        apiCall(mapper) { api.markRead(MarkRead(ids)) }
}
