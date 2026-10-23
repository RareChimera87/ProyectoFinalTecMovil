package me.santiagoreina.parcheu.feature.home

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.Suggestion
import me.santiagoreina.parcheu.contract.Usage
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.feature.simulation.errorMessage

/*
 * Textos de C1 calculados desde el DTO. Sin `java.time` (minSdk 24 sin desugaring): Bogotá no tiene
 * horario de verano, así que el día de la semana sale del epoch con UTC-5 fijo.
 */

private const val BOGOTA_OFFSET_SECONDS = -5 * 3600L
private const val SECONDS_PER_DAY = 86_400L
private val WEEKDAYS =
    listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

/** Día de la semana en Bogotá ("lunes"). El 1 de enero de 1970 fue jueves. */
internal fun weekdayInBogota(instant: Instant): String {
    val days = Math.floorDiv(instant.epochSeconds + BOGOTA_OFFSET_SECONDS, SECONDS_PER_DAY)
    return WEEKDAYS[Math.floorMod(days + 3, 7L).toInt()]
}

/** Lo que queda de un cupo con límite (nunca negativo); null si es ilimitado. */
internal fun Usage.remaining(): Int? = limit?.let { (it - used).coerceAtLeast(0) }

/** Pie de la tarjeta de cupo: "Te quedan 3 · se renuevan el lunes" o, sin cupo, "Ya usaste las 5 ...". */
internal fun quotaFootnote(usage: Usage): String {
    val limit = usage.limit ?: return "Sin límite semanal · llevas ${usage.used} hoy"
    val day = weekdayInBogota(usage.resetsAt)
    return when (val left = usage.remaining() ?: 0) {
        0 -> "Ya usaste las $limit · se renuevan el $day"
        1 -> "Te queda 1 · se renuevan el $day"
        else -> "Te quedan $left · se renuevan el $day"
    }
}

/** Pie de la tarjeta de match al azar (cupo diario). */
internal fun randomMatchFootnote(usage: Usage): String = when (usage.remaining()) {
    null -> "Sin límite diario"
    0 -> "Ya lo usaste hoy"
    1 -> "Te queda 1 hoy"
    else -> "Te quedan ${usage.remaining()} hoy"
}

/**
 * Segunda línea de una sugerencia. 'Amigos en común' es ParcheU+ y el servidor no lo envía a gratis
 * (inventario §6, decisión 19); sin él se usa el motivo del servidor.
 */
internal fun suggestionLine(suggestion: Suggestion): String {
    val mutual = suggestion.mutualCount
    return when {
        mutual != null && mutual > 0 -> if (mutual ==
            1
        ) {
            "1 amigo en común"
        } else {
            "$mutual amigos en común"
        }
        else -> suggestion.reason ?: "Vuelvan a simular"
    }
}

/** "hace 1 h", "hace 5 min", "hace 2 d". */
internal fun relativeTime(then: Instant, now: Instant): String {
    val seconds = (now - then).inWholeSeconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "hace un momento"
        seconds < 3600 -> "hace ${seconds / 60} min"
        seconds < SECONDS_PER_DAY -> "hace ${seconds / 3600} h"
        else -> "hace ${seconds / SECONDS_PER_DAY} d"
    }
}

/** Insignia de los perfiles demo (ADR-0027), la misma de D2, resultados y chat. */
internal const val DEMO_PROFILE_BADGE = "Perfil de demostración"

/**
 * Aviso de C1 tras un "Simular" fallido. Sin cupo solo llega aquí un ParcheU+ en el uso justo diario (a
 * gratis se le abre el paywall), así que no se habla de "la semana", como en D2.
 */
internal fun homeErrorMessage(error: AppError): String = when (error) {
    is AppError.QuotaExceeded -> "Ya usaste tus simulaciones disponibles por ahora."
    else -> errorMessage(error)
}
