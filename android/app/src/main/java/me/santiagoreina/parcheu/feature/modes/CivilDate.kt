package me.santiagoreina.parcheu.feature.modes

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import me.santiagoreina.parcheu.contract.IsoDate

/**
 * Fecha civil sin zona. Se usa en lugar de `java.time` porque la app soporta API 24 sin desugaring; la
 * edad que decide el acceso a Pareja la calcula el servidor (E2.6), esto solo la muestra en B3.
 */
data class CivilDate(val year: Int, val month: Int, val day: Int) {
    companion object {
        /** `yyyy-MM-dd` del contrato. */
        fun of(date: IsoDate): CivilDate = date.value.split("-").let { (y, m, d) ->
            CivilDate(y.toInt(), m.toInt(), d.toInt())
        }

        /** Hoy en Bogotá (la misma zona con la que el servidor calcula la edad). */
        fun todayInBogota(): CivilDate {
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("America/Bogota"))
            return CivilDate(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            )
        }
    }
}

/** Años cumplidos en [today]. */
fun CivilDate.ageOn(today: CivilDate): Int {
    val hadBirthday = today.month > month || (today.month == month && today.day >= day)
    return today.year - year - if (hadBirthday) 0 else 1
}

/** Formato de B3: `14 / 03 / 2005`. */
fun CivilDate.display(): String = String.format(Locale.ROOT, "%02d / %02d / %04d", day, month, year)
