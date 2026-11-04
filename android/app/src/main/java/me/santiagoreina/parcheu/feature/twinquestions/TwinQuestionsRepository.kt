package me.santiagoreina.parcheu.feature.twinquestions

import me.santiagoreina.parcheu.contract.Answers
import me.santiagoreina.parcheu.contract.QuestionDto
import me.santiagoreina.parcheu.contract.TwinDto
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall

/** Acceso a las preguntas del gemelo y al envío de las respuestas. */
interface TwinQuestionsRepository {
    suspend fun questions(): ApiResult<List<QuestionDto>>

    suspend fun submit(answers: Answers): ApiResult<TwinDto>
}

class TwinQuestionsRepositoryImpl(
    private val api: TwinQuestionsApi,
    private val mapper: ApiErrorMapper
) : TwinQuestionsRepository {
    override suspend fun questions(): ApiResult<List<QuestionDto>> = apiCall(mapper) {
        api.questions()
    }

    override suspend fun submit(answers: Answers): ApiResult<TwinDto> =
        apiCall(mapper, routesPremium = false) {
            api.submit(answers)
        }
}
