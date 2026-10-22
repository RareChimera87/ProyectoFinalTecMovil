package me.santiagoreina.parcheu.feature.basicprofile

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import me.santiagoreina.parcheu.contract.IsoDate

/**
 * Utilidades de fecha de nacimiento sin `java.time` (minSdk 24 sin desugaring). El selector de Material 3
 * entrega la fecha como medianoche UTC en milisegundos; la API recibe `yyyy-MM-dd`. La edad la decide el
 * servidor con la zona de Bogotá: aquí solo se convierte y se formatea.
 */
object BirthDates {
    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    private val MONTHS =
        listOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
        )

    private fun utcCalendar(): Calendar = Calendar.getInstance(UTC).apply { clear() }

    fun fromUtcMillis(millis: Long): IsoDate {
        val c = utcCalendar().apply { timeInMillis = millis }
        val y = c.get(Calendar.YEAR)
        val m = c.get(Calendar.MONTH) + 1
        val d = c.get(Calendar.DAY_OF_MONTH)
        return IsoDate(String.format(Locale.ROOT, "%04d-%02d-%02d", y, m, d))
    }

    fun toUtcMillis(date: IsoDate): Long = utcCalendar().apply { set(date.year, date.month - 1, date.day) }.timeInMillis

    /** "17 de mayo de 2004". */
    fun display(date: IsoDate): String = "${date.day} de ${MONTHS[date.month - 1]} de ${date.year}"

    /** Medianoche UTC de hoy menos [years] años: mes inicial del selector (alguien de 18 a 20 años). */
    fun yearsAgoUtcMillis(nowMillis: Long, years: Int): Long = utcCalendar().apply {
        timeInMillis = nowMillis
        add(Calendar.YEAR, -years)
    }.timeInMillis
}
