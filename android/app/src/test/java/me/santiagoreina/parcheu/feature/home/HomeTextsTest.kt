package me.santiagoreina.parcheu.feature.home

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.contract.Suggestion
import me.santiagoreina.parcheu.contract.Usage
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTextsTest {
    private val monday = Instant.parse("2026-11-09T05:00:00Z")

    @Test
    fun weekdayUsesBogotaTime() {
        assertEquals("lunes", weekdayInBogota(monday))
        // 04:59 UTC del lunes todavía es domingo en Bogotá.
        assertEquals("domingo", weekdayInBogota(Instant.parse("2026-11-09T04:59:00Z")))
        assertEquals("jueves", weekdayInBogota(Instant.parse("1970-01-01T12:00:00Z")))
    }

    @Test
    fun quotaFootnotes() {
        assertEquals("Te quedan 2 · se renuevan el lunes", quotaFootnote(Usage(3, 5, monday)))
        assertEquals("Te queda 1 · se renuevan el lunes", quotaFootnote(Usage(4, 5, monday)))
        assertEquals("Ya usaste las 5 · se renuevan el lunes", quotaFootnote(Usage(5, 5, monday)))
        assertEquals("Sin límite semanal · llevas 4 hoy", quotaFootnote(Usage(4, null, monday)))
        assertEquals("Ya lo usaste hoy", randomMatchFootnote(Usage(1, 1, monday)))
        assertEquals("Te queda 1 hoy", randomMatchFootnote(Usage(0, 1, monday)))
    }

    @Test
    fun suggestionLineOnlyShowsMutualsWhenTheServerSendsThem() {
        val free =
            Suggestion(
                PersonRef(id = "s", label = "Sebas", initials = "S"),
                Mode.AMISTAD,
                reason = "La última vez salieron 72 % afines"
            )
        assertEquals("La última vez salieron 72 % afines", suggestionLine(free))
        assertEquals("2 amigos en común", suggestionLine(free.copy(mutualCount = 2)))
        assertEquals("1 amigo en común", suggestionLine(free.copy(mutualCount = 1)))
        assertEquals(
            "La última vez salieron 72 % afines",
            suggestionLine(free.copy(mutualCount = 0))
        )
    }

    @Test
    fun relativeTimes() {
        val now = Instant.parse("2026-11-03T15:00:00Z")
        assertEquals("hace un momento", relativeTime(Instant.parse("2026-11-03T14:59:30Z"), now))
        assertEquals("hace 20 min", relativeTime(Instant.parse("2026-11-03T14:40:00Z"), now))
        assertEquals("hace 1 h", relativeTime(Instant.parse("2026-11-03T14:00:00Z"), now))
        assertEquals("hace 2 d", relativeTime(Instant.parse("2026-11-01T15:00:00Z"), now))
    }
}
