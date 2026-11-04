package me.santiagoreina.parcheu.data.billing

import me.santiagoreina.parcheu.data.billing.FakeBillingClient.Companion.productDetailsFromJson
import me.santiagoreina.parcheu.data.billing.FakeBillingClient.Companion.subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** E7.2: los precios del paywall salen de los micros de `ProductDetails`, nunca escritos a mano. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlusPlansTest {
    @Test
    fun annualMonthlyEquivalentComesFromProductDetailsMicros() {
        val plans = requireNotNull(PlusPlans.from(subscription()))

        assertEquals(45_000_000_000L, plans.annual.priceAmountMicros)
        assertEquals(3_750_000_000L, plans.annualMonthlyEquivalentMicros)
        assertEquals("$3.750", plans.formattedAnnualMonthlyEquivalent())
        assertEquals(15_000_000_000L, plans.annualSavingsMicros)
        assertEquals("$15.000", plans.formattedAnnualSavings())
    }

    @Test
    fun followsWhateverPricePlayReturns() {
        // Si Play Console cambia los precios, la app no tiene cifras fijas que se queden atrás.
        val plans =
            requireNotNull(
                PlusPlans.from(
                    subscription(
                        basePlans =
                        listOf(
                            PlusProduct.BASE_PLAN_MONTHLY to 6_000_000_000L,
                            PlusProduct.BASE_PLAN_ANNUAL to 60_000_000_000L
                        )
                    )
                )
            )

        assertEquals("$5.000", plans.formattedAnnualMonthlyEquivalent())
        assertEquals("$12.000", plans.formattedAnnualSavings())
    }

    @Test
    fun keepsTheBasePlanOfferTokenAndPrice() {
        val plans = requireNotNull(PlusPlans.from(subscription()))

        assertEquals("token-mensual", plans.monthly.offerToken)
        assertEquals("P1M", plans.monthly.billingPeriod)
        assertEquals("COP", plans.monthly.priceCurrencyCode)
        assertEquals("token-anual", plans.annual.offerToken)
        assertEquals(plans.annual, plans.plan(PlusProduct.BASE_PLAN_ANNUAL))
        assertNull(plans.plan("semanal"))
    }

    @Test
    fun ignoresPromotionalOffersAndUsesTheRecurringPhase() {
        val details =
            productDetailsFromJson(
                """
                {"productId":"parcheu_plus","type":"subs","title":"ParcheU+","name":"ParcheU+",
                 "description":"","localizedIn":["es-419"],"skuDetailsToken":"t",
                 "subscriptionOfferDetails":[
                  {"basePlanId":"anual","offerId":"promo","offerIdToken":"token-promo","offerTags":[],
                   "pricingPhases":[{"priceAmountMicros":0,"priceCurrencyCode":"COP","formattedPrice":"Gratis",
                    "billingPeriod":"P1W","recurrenceMode":2,"billingCycleCount":1},
                    {"priceAmountMicros":45000000000,"priceCurrencyCode":"COP","formattedPrice":"$ 45.000",
                    "billingPeriod":"P1Y","recurrenceMode":1,"billingCycleCount":0}]},
                  {"basePlanId":"anual","offerIdToken":"token-anual","offerTags":[],
                   "pricingPhases":[{"priceAmountMicros":45000000000,"priceCurrencyCode":"COP",
                    "formattedPrice":"$ 45.000","billingPeriod":"P1Y","recurrenceMode":1,"billingCycleCount":0}]},
                  {"basePlanId":"mensual","offerIdToken":"token-mensual","offerTags":[],
                   "pricingPhases":[{"priceAmountMicros":5000000000,"priceCurrencyCode":"COP",
                    "formattedPrice":"$ 5.000","billingPeriod":"P1M","recurrenceMode":1,"billingCycleCount":0}]}
                 ]}
                """.trimIndent()
            )

        val plans = requireNotNull(PlusPlans.from(details))

        assertEquals("token-anual", plans.annual.offerToken)
        assertEquals("$3.750", plans.formattedAnnualMonthlyEquivalent())
    }

    @Test
    fun isNullWhenABasePlanIsMissing() {
        assertNull(
            PlusPlans.from(
                subscription(basePlans = listOf(PlusProduct.BASE_PLAN_MONTHLY to 5_000_000_000L))
            )
        )
    }

    @Test
    fun isNullForAnotherProduct() {
        assertNull(PlusPlans.from(subscription(productId = "otro")))
        assertNotNull(PlusPlans.from(subscription()))
    }

    @Test
    fun formatsCentsOnlyWhenPresent() {
        assertEquals("US$4,99", PriceFormatter.format(4_990_000L, "USD"))
        assertEquals("$1.000.000", PriceFormatter.format(1_000_000_000_000L, "COP"))
    }
}
