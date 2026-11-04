package me.santiagoreina.parcheu.feature.plus

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.EntitlementDto
import me.santiagoreina.parcheu.contract.EntitlementState
import me.santiagoreina.parcheu.data.billing.PlusPlans
import me.santiagoreina.parcheu.data.billing.PlusProduct

/*
 * Textos de G1-G4 calculados desde los datos. Ningún precio se escribe a mano: salen de `PlusPlans`, que
 * se construye con los micros de `ProductDetails` (ADR-0029). Sin `java.time` (minSdk 24 sin desugaring):
 * Bogotá no tiene horario de verano, así que la fecha sale del epoch con UTC-5 fijo.
 */

/** Cifras del plan anual para G1 y G4: ya formateadas por Play; la UI agrega el texto alrededor. */
data class AnnualOffer(
    /** Ej. `$3.750` (anual / 12). La UI agrega `/mes`. */
    val perMonth: String,
    /** Ej. `$15.000`: lo que se ahorra al año frente a 12 mensualidades. `null` si no ahorra nada. */
    val savings: String?
)

fun PlusPlans.annualOffer(): AnnualOffer = AnnualOffer(
    perMonth = formattedAnnualMonthlyEquivalent(),
    savings = if (annualSavingsMicros > 0) formattedAnnualSavings() else null
)

private val MONTHS = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
)
private const val BOGOTA_OFFSET_SECONDS = -5 * 3600L
private const val SECONDS_PER_DAY = 86_400L

private data class CivilDate(val year: Int, val month: Int, val day: Int, val weekday: Int)

private val WEEKDAYS =
    listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

private fun civilDate(instant: Instant): CivilDate {
    val days = Math.floorDiv(instant.epochSeconds + BOGOTA_OFFSET_SECONDS, SECONDS_PER_DAY)
    // Fecha civil desde días del epoch (algoritmo de Howard Hinnant).
    val z = days + 719_468
    val era = Math.floorDiv(z, 146_097L)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
    val year = (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
    // El 1 de enero de 1970 fue jueves.
    val weekday = Math.floorMod(days + 3, 7L).toInt()
    return CivilDate(year, month, day, weekday)
}

/** "21 de octubre de 2027" en hora de Bogotá. */
fun formatLongDate(instant: Instant): String {
    val d = civilDate(instant)
    return "${d.day} de ${MONTHS[d.month - 1]} de ${d.year}"
}

/** "lunes 9 de noviembre" en hora de Bogotá (renovación de un cupo). */
fun formatWeekdayDate(instant: Instant): String {
    val d = civilDate(instant)
    return "${WEEKDAYS[d.weekday]} ${d.day} de ${MONTHS[d.month - 1]}"
}

/** Lo que G4 muestra según el derecho que devuelve la API. */
enum class SubscriptionStatus {
    /** Activa y con renovación automática. */
    Active,

    /** El usuario canceló: sigue con ParcheU+ hasta el fin del periodo pagado. */
    Canceled,

    /** Falló el cobro y Play reintenta: aún tiene ParcheU+ durante la gracia. */
    Grace,

    /** Cobro fallido sin gracia (en espera o pausada): sin ParcheU+ hasta arreglar el pago. */
    OnHold,

    /** Nunca compró, expiró o se reembolsó. */
    Inactive
}

fun EntitlementDto.status(): SubscriptionStatus = when {
    state == EntitlementState.IN_GRACE_PERIOD -> SubscriptionStatus.Grace
    state == EntitlementState.ON_HOLD || state == EntitlementState.PAUSED ->
        SubscriptionStatus.OnHold
    state == EntitlementState.CANCELED && plus -> SubscriptionStatus.Canceled
    state == EntitlementState.ACTIVE && plus ->
        if (autoRenewing == false) SubscriptionStatus.Canceled else SubscriptionStatus.Active
    else -> SubscriptionStatus.Inactive
}

val EntitlementDto.isAnnual: Boolean get() = basePlan == PlusProduct.BASE_PLAN_ANNUAL
val EntitlementDto.isMonthly: Boolean get() = basePlan == PlusProduct.BASE_PLAN_MONTHLY
