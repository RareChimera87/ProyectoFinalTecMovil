package me.santiagoreina.parcheu.feature.plus

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.EntitlementState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Los textos de precio y de ahorro salen de `ProductDetails`, nunca escritos a mano (ADR-0029). */
class PlusTextsTest {
    @Test
    fun annualOfferIsComputedFromMicros() {
        // Mensual $5.000 y anual $45.000: $3.750/mes y ahorro de $15.000 (3 meses, no "2 meses gratis").
        val offer = PlusTestData.plans.annualOffer()
        assertEquals("$3.750", offer.perMonth)
        assertEquals("$15.000", offer.savings)
    }

    @Test
    fun otherPricesProduceOtherTexts() {
        val offer = PlusTestData.otherPlans.annualOffer()
        assertEquals("$4.500", offer.perMonth)
        assertEquals("$18.000", offer.savings)
    }

    @Test
    fun noSavingsTextWhenAnnualIsNotCheaper() {
        val plans =
            PlusTestData.plans.copy(
                annual = PlusTestData.plans.annual.copy(priceAmountMicros = 60_000_000_000)
            )
        assertNull(plans.annualOffer().savings)
    }

    @Test
    fun longDateUsesBogotaTime() {
        assertEquals("21 de octubre de 2027", formatLongDate(PlusTestData.renewal))
        // 01:00 UTC del 1 de enero todavía es 31 de diciembre en Bogotá.
        assertEquals(
            "31 de diciembre de 2026",
            formatLongDate(Instant.parse("2027-01-01T01:00:00Z"))
        )
        assertEquals("29 de febrero de 2028", formatLongDate(Instant.parse("2028-02-29T15:00:00Z")))
    }

    @Test
    fun weekdayDateNamesTheDay() {
        // Lunes 9 de noviembre de 2026, 00:00 en Bogotá.
        assertEquals(
            "lunes 9 de noviembre",
            formatWeekdayDate(Instant.parse("2026-11-09T05:00:00Z"))
        )
    }

    @Test
    fun entitlementStatesMapToTheFourVariantsOfG4() {
        assertEquals(SubscriptionStatus.Active, PlusTestData.activeAnnual.status())
        assertEquals(SubscriptionStatus.Active, PlusTestData.activeMonthly.status())
        assertEquals(SubscriptionStatus.Canceled, PlusTestData.canceled.status())
        assertEquals(SubscriptionStatus.Grace, PlusTestData.grace.status())
        assertEquals(
            SubscriptionStatus.OnHold,
            PlusTestData.entitlement(EntitlementState.ON_HOLD).status()
        )
        assertEquals(SubscriptionStatus.Inactive, PlusTestData.free.status())
        assertEquals(
            SubscriptionStatus.Inactive,
            PlusTestData.entitlement(EntitlementState.EXPIRED).status()
        )
        // ACTIVE sin renovación automática equivale a cancelada (sigue hasta expiresAt).
        assertEquals(
            SubscriptionStatus.Canceled,
            PlusTestData.entitlement(autoRenewing = false).status()
        )
    }
}
