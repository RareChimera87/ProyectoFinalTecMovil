package me.santiagoreina.parcheu.data.billing

import com.android.billingclient.api.ProductDetails
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale

/**
 * Un plan base de `parcheu_plus` tal como lo devolvió Play. Todo precio que muestre la app sale de aquí
 * (micros de `ProductDetails`); ninguno se escribe a mano.
 */
data class PlusPlan(
    val basePlanId: String,
    /** Token de la oferta del plan base (sin promociones), requerido por `launchBillingFlow`. */
    val offerToken: String,
    val priceAmountMicros: Long,
    val priceCurrencyCode: String,
    /** Precio ya formateado por Play para la cuenta del dispositivo. */
    val formattedPrice: String,
    /** Periodo ISO 8601 (`P1M`, `P1Y`). */
    val billingPeriod: String
)

/**
 * Planes `mensual` y `anual` de ParcheU+ con los cálculos derivados que usa el paywall (E7.3):
 * equivalente mensual del anual y ahorro anual (ADR-0029). Se construye solo desde `ProductDetails`.
 */
data class PlusPlans(val monthly: PlusPlan, val annual: PlusPlan) {
    /** Precio mensual equivalente del anual: micros del anual / 12, redondeado al micro. */
    val annualMonthlyEquivalentMicros: Long
        get() =
            BigDecimal.valueOf(annual.priceAmountMicros)
                .divide(BigDecimal.valueOf(MONTHS_PER_YEAR), 0, RoundingMode.HALF_UP)
                .toLong()

    /** Lo que se ahorra al año pagando el anual frente a 12 mensualidades (nunca negativo). */
    val annualSavingsMicros: Long
        get() = (monthly.priceAmountMicros * MONTHS_PER_YEAR - annual.priceAmountMicros)
            .coerceAtLeast(0)

    /** Ej. `$3.750` para $45.000 al año. La UI agrega el sufijo `/mes`. */
    fun formattedAnnualMonthlyEquivalent(locale: Locale = PriceFormatter.DEFAULT_LOCALE): String =
        PriceFormatter.format(annualMonthlyEquivalentMicros, annual.priceCurrencyCode, locale)

    /** Ej. `$15.000` para $5.000/mes frente a $45.000/año. */
    fun formattedAnnualSavings(locale: Locale = PriceFormatter.DEFAULT_LOCALE): String =
        PriceFormatter.format(annualSavingsMicros, annual.priceCurrencyCode, locale)

    fun plan(basePlanId: String): PlusPlan? = when (basePlanId) {
        monthly.basePlanId -> monthly
        annual.basePlanId -> annual
        else -> null
    }

    companion object {
        private const val MONTHS_PER_YEAR = 12L

        /**
         * Extrae los planes de [details]. Devuelve `null` si no es `parcheu_plus` o si falta alguno de los
         * dos planes base (p. ej. uno inactivo en Play Console), para que la UI muestre el estado de error
         * en vez de un precio inventado.
         */
        fun from(details: ProductDetails): PlusPlans? {
            if (details.productId != PlusProduct.PRODUCT_ID) return null
            val offers = details.subscriptionOfferDetails.orEmpty()
            val monthly = basePlan(offers, PlusProduct.BASE_PLAN_MONTHLY) ?: return null
            val annual = basePlan(offers, PlusProduct.BASE_PLAN_ANNUAL) ?: return null
            return PlusPlans(monthly, annual)
        }

        private fun basePlan(
            offers: List<ProductDetails.SubscriptionOfferDetails>,
            basePlanId: String
        ): PlusPlan? {
            // La oferta del plan base es la que no tiene offerId; las demás son promociones o pruebas.
            val offer =
                offers.firstOrNull { it.basePlanId == basePlanId && it.offerId == null }
                    ?: return null
            // Con promociones hay varias fases; la recurrente (la última) es el precio real del plan.
            val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return null
            return PlusPlan(
                basePlanId = basePlanId,
                offerToken = offer.offerToken,
                priceAmountMicros = phase.priceAmountMicros,
                priceCurrencyCode = phase.priceCurrencyCode,
                formattedPrice = phase.formattedPrice,
                billingPeriod = phase.billingPeriod
            )
        }
    }
}

/** Formatea micros de Play como precio ("$3.750"); muestra decimales solo si los hay. */
object PriceFormatter {
    val DEFAULT_LOCALE: Locale = Locale.forLanguageTag("es-CO")
    private const val MICROS_PER_UNIT = 1_000_000L

    fun format(micros: Long, currencyCode: String, locale: Locale = DEFAULT_LOCALE): String {
        val currency = Currency.getInstance(currencyCode)
        val amount = BigDecimal.valueOf(micros).divide(BigDecimal.valueOf(MICROS_PER_UNIT))
        val digits = currency.defaultFractionDigits.coerceAtLeast(0)
        val rounded = amount.setScale(digits, RoundingMode.HALF_UP)
        val hasFraction = rounded.stripTrailingZeros().scale() > 0
        val format =
            DecimalFormat(if (hasFraction) "#,##0.${"0".repeat(digits)}" else "#,##0").apply {
                decimalFormatSymbols = DecimalFormatSymbols.getInstance(locale)
                roundingMode = RoundingMode.HALF_UP
            }
        return currency.getSymbol(locale) + format.format(rounded)
    }
}
