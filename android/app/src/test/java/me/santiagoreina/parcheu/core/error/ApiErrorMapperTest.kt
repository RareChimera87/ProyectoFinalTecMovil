package me.santiagoreina.parcheu.core.error

import java.io.IOException
import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.ApiError
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.ErrorCode
import me.santiagoreina.parcheu.contract.QuotaKind
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ApiErrorMapperTest {
    private val mapper = ApiErrorMapper()

    private fun body(error: ApiError) = ContractJson.encodeToString(ApiError.serializer(), error)

    @Test
    fun quotaExceeded402MapsToQuotaExceededWithKindAndResetsAt() {
        val resetsAt = Instant.parse("2026-10-23T05:00:00Z")
        val json =
            body(
                ApiError(
                    ErrorCode.QUOTA_EXCEEDED,
                    "Sin cupo",
                    kind = "RANDOM_MATCH",
                    resetsAt = resetsAt,
                ),
            )

        assertEquals(
            AppError.QuotaExceeded(QuotaKind.RANDOM_MATCH, resetsAt),
            mapper.map(402, json),
        )
    }

    @Test
    fun plusRequired402MapsToPlusRequired() {
        assertEquals(
            AppError.PlusRequired,
            mapper.map(402, body(ApiError(ErrorCode.PLUS_REQUIRED, "ParcheU+"))),
        )
    }

    @Test
    fun retrofitHttpExceptionIsMappedFromItsErrorBody() {
        val resetsAt = "2026-10-26T05:00:00Z"
        val json =
            """{"code":"QUOTA_EXCEEDED","message":"x","kind":"SIMULATION","resetsAt":"$resetsAt"}"""
        val jsonType = "application/json".toMediaType()
        val response = Response.error<Unit>(402, json.toResponseBody(jsonType))

        assertEquals(
            AppError.QuotaExceeded(QuotaKind.SIMULATION, Instant.parse(resetsAt)),
            mapper.map(HttpException(response)),
        )
    }

    @Test
    fun everyErrorCodeHasADedicatedAppError() {
        val expected =
            mapOf(
                ErrorCode.VALIDATION to AppError.Validation("m"),
                ErrorCode.UNAUTHENTICATED to AppError.Unauthorized,
                ErrorCode.PLUS_REQUIRED to AppError.PlusRequired,
                ErrorCode.QUOTA_EXCEEDED to AppError.QuotaExceeded(null, null),
                ErrorCode.TERMS_REQUIRED to AppError.TermsRequired,
                ErrorCode.AGE_REQUIRED to AppError.AgeRequired,
                ErrorCode.UNDERAGE to AppError.Underage,
                ErrorCode.BLOCKED to AppError.Blocked,
                ErrorCode.SUSPENDED to AppError.Suspended,
                ErrorCode.FORBIDDEN to AppError.Forbidden,
                ErrorCode.NOT_FOUND to AppError.NotFound,
                ErrorCode.CONFLICT to AppError.Conflict,
                ErrorCode.SCENARIO_ENDED to AppError.ScenarioEnded,
                ErrorCode.AI_BUDGET to AppError.AiBudget(null),
            )
        assertEquals(ErrorCode.entries.toSet(), expected.keys)
        expected.forEach { (code, appError) ->
            assertEquals(
                code.name,
                appError,
                mapper.map(code.httpStatus, body(ApiError(code, "m"))),
            )
        }
    }

    @Test
    fun unreadableBodyFallsBackToStatus() {
        assertEquals(AppError.NotFound, mapper.map(404, "<html>no</html>"))
        assertEquals(AppError.Unknown(httpStatus = 500), mapper.map(500, null))
    }

    @Test
    fun ioExceptionIsNetwork() {
        assertEquals(AppError.Network, mapper.map(IOException("sin red")))
    }
}
