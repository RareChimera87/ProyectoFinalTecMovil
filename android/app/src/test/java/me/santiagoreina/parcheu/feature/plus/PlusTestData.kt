package me.santiagoreina.parcheu.feature.plus

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.EntitlementDto
import me.santiagoreina.parcheu.contract.EntitlementState
import me.santiagoreina.parcheu.data.billing.PlusPlan
import me.santiagoreina.parcheu.data.billing.PlusPlans

/** Datos de G1-G4: los precios del producto son mensual $5.000 y anual $45.000 (micros de Play). */
object PlusTestData {
    private fun plan(id: String, cop: Long, formatted: String, period: String) = PlusPlan(
        basePlanId = id,
        offerToken = "token-$id",
        priceAmountMicros = cop * 1_000_000,
        priceCurrencyCode = "COP",
        formattedPrice = formatted,
        billingPeriod = period
    )

    val plans =
        PlusPlans(
            monthly = plan("mensual", 5_000, "$5.000", "P1M"),
            annual = plan("anual", 45_000, "$45.000", "P1Y")
        )

    /** Otros precios, para probar que ninguno está escrito a mano. */
    val otherPlans =
        PlusPlans(
            monthly = plan("mensual", 6_000, "$6.000", "P1M"),
            annual = plan("anual", 54_000, "$54.000", "P1Y")
        )

    /** 21 de octubre de 2027, 12:00 en Bogotá. */
    val renewal: Instant = Instant.parse("2027-10-21T17:00:00Z")

    fun entitlement(
        state: EntitlementState = EntitlementState.ACTIVE,
        basePlan: String? = "anual",
        plus: Boolean = state == EntitlementState.ACTIVE ||
            state == EntitlementState.IN_GRACE_PERIOD ||
            state == EntitlementState.CANCELED,
        autoRenewing: Boolean? = state == EntitlementState.ACTIVE
    ) = EntitlementDto(
        plus = plus,
        state = state,
        basePlan = basePlan,
        expiresAt = renewal,
        autoRenewing = autoRenewing
    )

    val activeAnnual = entitlement()
    val activeMonthly = entitlement(basePlan = "mensual")
    val canceled = entitlement(EntitlementState.CANCELED)
    val grace = entitlement(EntitlementState.IN_GRACE_PERIOD, autoRenewing = true)
    val paused = entitlement(EntitlementState.PAUSED, autoRenewing = false)
    val free = EntitlementDto(plus = false, state = EntitlementState.NONE)
}
