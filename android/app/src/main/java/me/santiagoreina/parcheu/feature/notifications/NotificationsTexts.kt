package me.santiagoreina.parcheu.feature.notifications

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.NotificationDto
import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.feature.home.relativeTime
import me.santiagoreina.parcheu.feature.simulation.errorMessage

/*
 * Textos de C2. Sin `java.time` (minSdk 24 sin desugaring): Bogotá no tiene horario de verano, así que el
 * día sale del epoch con UTC-5 fijo, igual que en C1.
 */

private const val BOGOTA_OFFSET_SECONDS = -5 * 3600L
private const val SECONDS_PER_DAY = 86_400L
private val WEEKDAYS_SHORT = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
private val MONTHS_SHORT =
    listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** Días desde el epoch en hora de Bogotá. */
private fun bogotaDay(instant: Instant): Long =
    Math.floorDiv(instant.epochSeconds + BOGOTA_OFFSET_SECONDS, SECONDS_PER_DAY)

/** Encabezados de los grupos (el backend ya agrupa con la fecha de Bogotá). */
internal const val SECTION_TODAY = "Hoy"
internal const val SECTION_WEEK = "Esta semana"
internal const val SECTION_OLDER = "Anteriores"

/** Pie del aviso bloqueado (mockup 05: "Míralas con ParcheU+ · mar"). */
internal const val LOCKED_HINT = "Míralas con ParcheU+"

/**
 * Hora del aviso: hoy "hace 1 h"; en la semana el día corto ("mié"); antes, la fecha ("12 oct").
 */
internal fun notificationTime(createdAt: Instant, now: Instant): String {
    val days = bogotaDay(now) - bogotaDay(createdAt)
    return when {
        days <= 0 -> relativeTime(createdAt, now)
        days < 7 -> WEEKDAYS_SHORT[Math.floorMod(bogotaDay(createdAt) + 3, 7L).toInt()]
        else -> civilDate(bogotaDay(createdAt))
    }
}

/** "12 oct" a partir de días desde el epoch (algoritmo civil de Howard Hinnant). */
private fun civilDate(epochDay: Long): String {
    val z = epochDay + 719_468
    val era = Math.floorDiv(z, 146_097L)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    return "$day ${MONTHS_SHORT[(month - 1).toInt()]}"
}

/** Categoría que acompaña la hora ("Resultados · hace 1 h"). */
internal fun categoryOf(kind: String): String = when (kind) {
    NotificationKinds.SIMULATION_READY, NotificationKinds.SIMULATION_FAILED -> "Resultados"
    NotificationKinds.MATCH_FOUND, NotificationKinds.MATCH_EXPIRED -> "Conocer"
    NotificationKinds.CONNECT_REQUEST, NotificationKinds.NEW_CONNECTION,
    NotificationKinds.INVITE_PENDING -> "Tu parche"
    NotificationKinds.INTRODUCTION_PROPOSAL, NotificationKinds.INTRODUCTION_READY,
    NotificationKinds.INTRODUCTION_REQUEST -> "Presentación"
    NotificationKinds.CHAT_MESSAGE -> "Chat"
    NotificationKinds.TWIN_MEMORIES, NotificationKinds.TWIN_FEEDBACK -> "Gemelo"
    NotificationKinds.COMMUNITY -> "Comunidades"
    else -> "Sistema"
}

/** Línea inferior de la fila: categoría (o el upsell si está bloqueado) y hora. */
internal fun footerOf(item: NotificationDto, now: Instant): String {
    val lead = if (item.locked) LOCKED_HINT else categoryOf(item.kind)
    return "$lead · ${notificationTime(item.createdAt, now)}"
}

internal fun notificationsErrorMessage(error: AppError): String = errorMessage(error)
