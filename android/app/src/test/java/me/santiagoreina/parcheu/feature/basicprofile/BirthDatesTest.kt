package me.santiagoreina.parcheu.feature.basicprofile

import me.santiagoreina.parcheu.contract.IsoDate
import org.junit.Assert.assertEquals
import org.junit.Test

class BirthDatesTest {
    @Test
    fun convertsPickerMillisToIsoDateAndBack() {
        val date = IsoDate("2004-05-17")
        val millis = BirthDates.toUtcMillis(date)
        assertEquals(1084752000000L, millis)
        assertEquals(date, BirthDates.fromUtcMillis(millis))
        assertEquals(
            IsoDate("2008-02-29"),
            BirthDates.fromUtcMillis(BirthDates.toUtcMillis(IsoDate("2008-02-29")))
        )
    }

    @Test
    fun displaysInSpanish() {
        assertEquals("17 de mayo de 2004", BirthDates.display(IsoDate("2004-05-17")))
        assertEquals("1 de diciembre de 1999", BirthDates.display(IsoDate("1999-12-01")))
    }

    @Test
    fun yearsAgoKeepsTheDay() {
        val now = BirthDates.toUtcMillis(IsoDate("2026-10-22"))
        assertEquals(
            IsoDate("2007-10-22"),
            BirthDates.fromUtcMillis(BirthDates.yearsAgoUtcMillis(now, 19))
        )
    }
}
