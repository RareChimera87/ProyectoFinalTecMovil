package me.santiagoreina.parcheu.feature.twinquestions

import app.cash.turbine.test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.Answers
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.QuestionDto
import me.santiagoreina.parcheu.contract.TwinDto
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.error.GlobalErrorBus
import me.santiagoreina.parcheu.core.network.ApiResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** El 402 sin `kind` de los reenvíos del gemelo (B-M1) lo muestra la pantalla; no debe abrir G1 por el bus. */
class TwinQuestionsRepositoryErrorsTest {
    private val bus = GlobalErrorBus(hasSession = { true })
    private val resetsAt = Instant.parse("2026-10-30T05:00:00Z")

    private val api = object : TwinQuestionsApi {
        override suspend fun questions(): List<QuestionDto> = emptyList()

        override suspend fun submit(answers: Answers): TwinDto {
            val body = ContractJson.encodeToString(
                ApiError.serializer(),
                ApiError(ErrorCode.QUOTA_EXCEEDED, "Cupo agotado", resetsAt = resetsAt)
            )
            throw HttpException(
                Response.error<Unit>(402, body.toResponseBody("application/json".toMediaType()))
            )
        }
    }

    @Test
    fun dailySubmitCapReachesTheScreenWithoutOpeningPlus() = runTest {
        val repository = TwinQuestionsRepositoryImpl(api, ApiErrorMapper(globalErrors = bus))
        bus.errors.test {
            val result = repository.submit(Answers(emptyList()))
            assertEquals(ApiResult.Failure(AppError.QuotaExceeded(null, resetsAt)), result)
            expectNoEvents()
        }
    }
}
