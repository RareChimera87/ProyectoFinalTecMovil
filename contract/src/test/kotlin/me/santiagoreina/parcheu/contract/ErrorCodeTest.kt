package me.santiagoreina.parcheu.contract

import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorCodeTest {

    private val expected = mapOf(
        ErrorCode.VALIDATION to 400,
        ErrorCode.UNAUTHENTICATED to 401,
        ErrorCode.PLUS_REQUIRED to 402,
        ErrorCode.QUOTA_EXCEEDED to 402,
        ErrorCode.TERMS_REQUIRED to 403,
        ErrorCode.AGE_REQUIRED to 403,
        ErrorCode.UNDERAGE to 403,
        ErrorCode.BLOCKED to 403,
        ErrorCode.SUSPENDED to 403,
        ErrorCode.FORBIDDEN to 403,
        ErrorCode.NOT_FOUND to 404,
        ErrorCode.CONFLICT to 409,
        ErrorCode.SCENARIO_ENDED to 409,
        ErrorCode.AI_BUDGET to 503,
    )

    @Test
    fun `cada ErrorCode tiene el status HTTP de arquitectura 5_1`() {
        assertEquals(expected.keys, ErrorCode.entries.toSet(), "Hay códigos sin status esperado declarado")
        ErrorCode.entries.forEach { code ->
            assertEquals(expected.getValue(code), code.httpStatus, "Status de $code")
        }
    }

    @Test
    fun `todo status es un codigo de error HTTP`() {
        ErrorCode.entries.forEach { assert(it.httpStatus in 400..599) { "$it" } }
    }
}
