package me.santiagoreina.parcheu.feature.twinquestions

import me.santiagoreina.parcheu.contract.Answers
import me.santiagoreina.parcheu.contract.QuestionDto
import me.santiagoreina.parcheu.contract.TwinAnswer
import me.santiagoreina.parcheu.contract.TwinDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

fun sampleQuestions(count: Int = 12): List<QuestionDto> = (1..count).map {
    QuestionDto(
        id = it,
        text = "Pregunta $it",
        optionA = "Opción A$it",
        optionB = "Opción B$it"
    )
}

fun sampleTwin() = TwinDto(level = 1, xp = 0, xpNext = 500, version = 1, personaReady = false)

class FakeTwinQuestionsRepository(
    var questions: ApiResult<List<QuestionDto>> = ApiResult.Success(sampleQuestions())
) : TwinQuestionsRepository {
    val submitted = mutableListOf<Answers>()
    var submitResults: MutableList<ApiResult<TwinDto>> = mutableListOf()

    override suspend fun questions() = questions

    override suspend fun submit(answers: Answers): ApiResult<TwinDto> {
        submitted += answers
        return if (submitResults.isEmpty()) {
            ApiResult.Success(
                sampleTwin()
            )
        } else {
            submitResults.removeAt(0)
        }
    }
}

class InMemoryAnswerProgressStore(initial: Map<Int, TwinAnswer> = emptyMap()) :
    AnswerProgressStore {
    var data: Map<Int, TwinAnswer> = initial

    override suspend fun load() = data

    override suspend fun save(answers: Map<Int, TwinAnswer>) {
        data = answers
    }

    override suspend fun clear() {
        data = emptyMap()
    }
}

val networkFailure: ApiResult<TwinDto> = ApiResult.Failure(AppError.Network)
