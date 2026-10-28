package me.santiagoreina.parcheu.feature.traintwin

import me.santiagoreina.parcheu.contract.Feedback
import me.santiagoreina.parcheu.contract.FeedbackPrompt
import me.santiagoreina.parcheu.contract.MemoryDto
import me.santiagoreina.parcheu.contract.Page
import me.santiagoreina.parcheu.contract.TwinDto
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Rutas de la API v1 que usa C4 (arquitectura.md §5.2). */
interface TrainTwinApi {
    @GET("v1/twin/memories")
    suspend fun memories(
        @Query("status") status: String,
        @Query("limit") limit: Int
    ): Page<MemoryDto>

    @POST("v1/twin/memories/{id}/accept")
    suspend fun accept(@Path("id") id: String): MemoryDto

    @POST("v1/twin/memories/{id}/reject")
    suspend fun reject(@Path("id") id: String): MemoryDto

    @GET("v1/twin/feedback-prompts")
    suspend fun prompts(): List<FeedbackPrompt>

    @POST("v1/twin/feedback")
    suspend fun feedback(@Body body: Feedback): TwinDto
}

/** Datos de C4. La UI depende solo de esta interfaz; las pruebas usan un repositorio en memoria. */
interface TrainTwinRepository {
    suspend fun pendingMemories(): ApiResult<List<MemoryDto>>

    suspend fun acceptedMemories(): ApiResult<List<MemoryDto>>

    suspend fun accept(id: String): ApiResult<MemoryDto>

    suspend fun reject(id: String): ApiResult<MemoryDto>

    /** La pregunta de control (si toca esta semana) y las respuestas por calificar. */
    suspend fun prompts(): ApiResult<List<FeedbackPrompt>>

    suspend fun rate(promptId: String, positive: Boolean): ApiResult<TwinDto>

    suspend fun answerControl(promptId: String, answer: String): ApiResult<TwinDto>
}

class RemoteTrainTwinRepository(private val api: TrainTwinApi, private val mapper: ApiErrorMapper) :
    TrainTwinRepository {
    override suspend fun pendingMemories() = apiCall(mapper) {
        api.memories("PENDING", PAGE_SIZE).items
    }

    override suspend fun acceptedMemories() = apiCall(mapper) {
        api.memories("ACCEPTED", PAGE_SIZE).items
    }

    override suspend fun accept(id: String) = apiCall(mapper) { api.accept(id) }

    override suspend fun reject(id: String) = apiCall(mapper) { api.reject(id) }

    override suspend fun prompts() = apiCall(mapper) { api.prompts() }

    override suspend fun rate(promptId: String, positive: Boolean) =
        apiCall(mapper) { api.feedback(Feedback(promptId, rating = if (positive) 1 else 0)) }

    override suspend fun answerControl(promptId: String, answer: String) =
        apiCall(mapper) { api.feedback(Feedback(promptId, answer = answer)) }

    private companion object {
        const val PAGE_SIZE = 50
    }
}
