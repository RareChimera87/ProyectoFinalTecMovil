package me.santiagoreina.parcheu.data.billing

import java.io.IOException
import me.santiagoreina.parcheu.contract.EntitlementDto
import me.santiagoreina.parcheu.contract.EntitlementState
import me.santiagoreina.parcheu.contract.VerifyPurchase

val PLUS_ACTIVE = EntitlementDto(plus = true, state = EntitlementState.ACTIVE, basePlan = "anual")
val FREE = EntitlementDto(plus = false, state = EntitlementState.NONE)

/** [BillingApi] en memoria: registra cada `verify` y responde con [verifyResponse]. */
class FakeBillingApi(
    var verifyResponse: EntitlementDto = PLUS_ACTIVE,
    var entitlementResponse: EntitlementDto = FREE
) : BillingApi {
    val verifyCalls = mutableListOf<VerifyPurchase>()
    var entitlementCalls = 0
        private set

    /** Si no es null, `verify` lanza esta excepción (p. ej. [IOException] para simular sin red). */
    var verifyFailure: Throwable? = null

    override suspend fun verify(body: VerifyPurchase): EntitlementDto {
        verifyCalls += body
        verifyFailure?.let { throw it }
        return verifyResponse
    }

    override suspend fun entitlement(): EntitlementDto {
        entitlementCalls++
        return entitlementResponse
    }

    companion object {
        fun offline() = FakeBillingApi().apply { verifyFailure = IOException("sin red") }
    }
}
