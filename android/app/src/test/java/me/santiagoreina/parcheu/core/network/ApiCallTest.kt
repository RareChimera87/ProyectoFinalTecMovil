package me.santiagoreina.parcheu.core.network

import app.cash.turbine.test
import java.io.IOException
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.error.GlobalError
import me.santiagoreina.parcheu.core.error.GlobalErrorBus
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** `apiCall` es el punto único que alimenta el bus global (arquitectura.md §2.3). */
class ApiCallTest {
    private val bus = GlobalErrorBus(hasSession = { true })
    private val mapper = ApiErrorMapper().apply { globalErrors = bus }

    private fun httpError(status: Int, code: ErrorCode): HttpException {
        val body = ContractJson.encodeToString(ApiError.serializer(), ApiError(code, "x"))
        return HttpException(
            Response.error<Unit>(status, body.toResponseBody("application/json".toMediaType()))
        )
    }

    @Test
    fun failurePublishesToTheBusAndStillReturnsTheError() = runTest {
        bus.errors.test {
            val result = apiCall(mapper) { throw httpError(401, ErrorCode.UNAUTHENTICATED) }
            assertEquals(ApiResult.Failure(AppError.Unauthorized), result)
            assertEquals(GlobalError.SessionExpired, awaitItem())

            val plus = apiCall(mapper) { throw httpError(402, ErrorCode.PLUS_REQUIRED) }
            assertEquals(ApiResult.Failure(AppError.PlusRequired), plus)
            assertEquals(GlobalError.PlusRequired, awaitItem())
        }
    }

    @Test
    fun successAndLocalErrorsPublishNothing() = runTest {
        bus.errors.test {
            assertEquals(ApiResult.Success(1), apiCall(mapper) { 1 })
            assertEquals(
                ApiResult.Failure(AppError.Network),
                apiCall(mapper) { throw IOException("sin red") }
            )
            expectNoEvents()
        }
    }

    @Test
    fun optedOutCallKeepsItsPremiumErrorButNotItsSessionError() = runTest {
        bus.errors.test {
            val locked =
                apiCall(mapper, routesPremium = false) {
                    throw httpError(402, ErrorCode.PLUS_REQUIRED)
                }
            assertEquals(ApiResult.Failure(AppError.PlusRequired), locked)
            expectNoEvents()
            apiCall(mapper, routesPremium = false) { throw httpError(403, ErrorCode.SUSPENDED) }
            assertEquals(GlobalError.AccountSuspended, awaitItem())
        }
    }

    @Test
    fun mapperWithoutBusOnlyReturnsTheError() = runTest {
        val result = apiCall(ApiErrorMapper()) { throw httpError(401, ErrorCode.UNAUTHENTICATED) }
        assertEquals(ApiResult.Failure(AppError.Unauthorized), result)
    }
}
