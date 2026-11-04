package me.santiagoreina.parcheu.feature.evolution

import kotlin.time.Instant

private val MONTH_NAMES =
    listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

/** Colombia está en UTC-5 todo el año (sin horario de verano). */
private const val BOGOTA_OFFSET_SECONDS = -5 * 3600L

/** "2026-07" -> "Julio"; un valor mal formado se devuelve tal cual. */
fun monthName(month: String): String {
    val n = month.substringAfter('-', "").toIntOrNull() ?: return month
    return MONTH_NAMES.getOrNull(n - 1) ?: month
}

/** "Jul", "Sep"...: tres letras del mes en que ocurrió [at] (hora de Bogotá). */
fun shortMonthOf(at: Instant): String {
    val days = Math.floorDiv(at.epochSeconds + BOGOTA_OFFSET_SECONDS, 86_400L)
    return MONTH_NAMES[civilMonth(days) - 1].take(3)
}

/** Mes (1-12) del día [epochDay] (algoritmo civil_from_days de H. Hinnant). */
private fun civilMonth(epochDay: Long): Int {
    val z = epochDay + 719_468
    val era = Math.floorDiv(z, 146_097L)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    return (if (mp < 10) mp + 3 else mp - 9).toInt()
}

/** "1 conexión" / "4 conexiones". */
fun connectionsText(n: Int): String = if (n == 1) "1 conexión" else "$n conexiones"
