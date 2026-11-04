package me.santiagoreina.parcheu.api.identity.profile

import me.santiagoreina.parcheu.api.platform.BogotaZone
import java.time.Clock
import java.time.LocalDate
import java.time.Period

/**
 * Reglas de edad de ParcheU (ADR-0031). La app es 18+ y la edad se calcula siempre con la fecha de
 * hoy en America/Bogota: quien cumple 18 hoy es mayor de edad desde las 00:00 hora de Bogotá, sin
 * importar la hora UTC del servidor.
 */
object AgeRules {
    const val ADULT_AGE = 18

    /** Fecha más antigua que se acepta como nacimiento (descarta fechas absurdas). */
    val MIN_BIRTH_DATE: LocalDate = LocalDate.of(1900, 1, 1)

    /** Fecha de hoy en Bogotá según [clock]. */
    fun todayInBogota(clock: Clock): LocalDate = clock.instant().atZone(BogotaZone).toLocalDate()

    /** Años cumplidos el día [today]. Un 29 de febrero cumple el 1 de marzo en años no bisiestos. */
    fun ageOn(birthDate: LocalDate, today: LocalDate): Int = Period.between(birthDate, today).years

    fun isAdult(birthDate: LocalDate, today: LocalDate): Boolean = ageOn(birthDate, today) >= ADULT_AGE

    /**
     * Rango de edad que muestra el `AnonProfile` del match (F2): 18-19, 20-22, 23-25 o 26+. Nunca se
     * expone la edad exacta ni la fecha. Un menor de edad no tiene rango (no puede usar la app).
     */
    fun ageRange(age: Int): String {
        require(age >= ADULT_AGE) { "Un menor de edad no tiene rango de edad" }
        return when {
            age <= 19 -> "18-19"
            age <= 22 -> "20-22"
            age <= 25 -> "23-25"
            else -> "26+"
        }
    }

    fun ageRange(birthDate: LocalDate, today: LocalDate): String = ageRange(ageOn(birthDate, today))
}
