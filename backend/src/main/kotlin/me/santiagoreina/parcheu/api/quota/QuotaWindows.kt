package me.santiagoreina.parcheu.api.quota

import me.santiagoreina.parcheu.api.platform.BogotaZone
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** Periodos de cupo. Todos se cortan a las 00:00 hora de Bogotá (UTC-5, sin horario de verano). */
enum class QuotaPeriod { DAY, ISO_WEEK, MONTH }

/** Ventana semiabierta `[start, end)`; `end` es el `resetsAt` que ve el cliente. */
data class QuotaWindow(val start: Instant, val end: Instant)

/**
 * Calcula la ventana de [period] que contiene [now]. No usa la zona del `Clock` sino siempre
 * [BogotaZone], para que un reloj de pruebas en UTC no cambie los cortes.
 */
fun windowFor(period: QuotaPeriod, now: Instant): QuotaWindow {
    val today = now.atZone(BogotaZone).toLocalDate()
    val start: ZonedDateTime
    val end: ZonedDateTime
    when (period) {
        QuotaPeriod.DAY -> {
            start = today.atStartOfDay(BogotaZone)
            end = today.plusDays(1).atStartOfDay(BogotaZone)
        }
        QuotaPeriod.ISO_WEEK -> {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            start = monday.atStartOfDay(BogotaZone)
            end = monday.plusWeeks(1).atStartOfDay(BogotaZone)
        }
        QuotaPeriod.MONTH -> {
            val first = today.withDayOfMonth(1)
            start = first.atStartOfDay(BogotaZone)
            end = first.plusMonths(1).atStartOfDay(BogotaZone)
        }
    }
    return QuotaWindow(start.toInstant(), end.toInstant())
}
