package me.santiagoreina.parcheu.feature.presentar

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.GraphDto
import me.santiagoreina.parcheu.contract.IntroResponse
import me.santiagoreina.parcheu.contract.Introduce
import me.santiagoreina.parcheu.contract.IntroductionDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.QuotaDto
import me.santiagoreina.parcheu.contract.ReportDto
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.error.GlobalError
import me.santiagoreina.parcheu.core.error.GlobalErrorBus
import me.santiagoreina.parcheu.core.network.ApiResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** E1 trata su 402 con su propio aviso: la llamada de presentar no abre G2 por el bus (B2 de #109). */
class RemoteIntroductionsRepositoryTest {
    private val bus = GlobalErrorBus(hasSession = { true })
    private val mapper = ApiErrorMapper(globalErrors = bus)

    private fun httpError(status: Int, code: ErrorCode): HttpException {
        val body = ContractJson.encodeToString(ApiError.serializer(), ApiError(code, "x"))
        return HttpException(
            Response.error<Unit>(status, body.toResponseBody("application/json".toMediaType()))
        )
    }

    private class FailingApi(private val error: Exception) : IntroductionsApi {
        override suspend fun graph(mode: Mode): GraphDto = throw error

        override suspend fun quota(): QuotaDto = throw error

        override suspend fun introduce(body: Introduce): IntroductionDto = throw error

        override suspend fun introduction(id: String): IntroductionDto = throw error

        override suspend fun respond(id: String, body: IntroResponse): IntroductionDto = throw error

        override suspend fun unlock(simulationId: String): ReportDto = throw error
    }

    @Test
    fun quotaExceededOnIntroduceStaysInE1() = runTest {
        val repository =
            RemoteIntroductionsRepository(
                FailingApi(httpError(402, ErrorCode.QUOTA_EXCEEDED)),
                mapper
            )
        bus.errors.test {
            val result = repository.introduce(Mode.AMISTAD, "a", "b")
            assertTrue((result as ApiResult.Failure).error is AppError.QuotaExceeded)
            expectNoEvents()
        }
    }

    @Test
    fun aSessionErrorOnIntroduceStillReachesTheBus() = runTest {
        val repository =
            RemoteIntroductionsRepository(
                FailingApi(httpError(401, ErrorCode.UNAUTHENTICATED)),
                mapper
            )
        bus.errors.test {
            repository.introduce(Mode.AMISTAD, "a", "b")
            assertEquals(GlobalError.SessionExpired, awaitItem())
        }
    }
}
