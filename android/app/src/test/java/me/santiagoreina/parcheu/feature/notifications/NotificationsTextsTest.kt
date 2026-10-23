package me.santiagoreina.parcheu.feature.notifications

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.NotificationKinds
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationsTextsTest {
    // Martes 3 de noviembre de 2026, 10:00 en Bogotá.
    private val now = Instant.parse("2026-11-03T15:00:00Z")

    @Test
    fun todayIsRelative() {
        assertEquals("hace 1 h", notificationTime(Instant.parse("2026-11-03T14:00:00Z"), now))
        // 00:05 de hoy en Bogotá.
        assertEquals("hace 9 h", notificationTime(Instant.parse("2026-11-03T05:05:00Z"), now))
    }

    @Test
    fun thisWeekShowsTheBogotaWeekday() {
        // 23:50 del lunes 2 en Bogotá (04:50Z del martes): lunes, no "hace 5 h".
        assertEquals("lun", notificationTime(Instant.parse("2026-11-03T04:50:00Z"), now))
        assertEquals("mié", notificationTime(Instant.parse("2026-10-28T15:00:00Z"), now))
    }

    @Test
    fun olderShowsTheDate() {
        assertEquals("27 oct", notificationTime(Instant.parse("2026-10-27T15:00:00Z"), now))
        assertEquals("1 ene", notificationTime(Instant.parse("2026-01-01T15:00:00Z"), now))
    }

    @Test
    fun footerShowsCategoryOrUpsell() {
        assertEquals("Resultados · hace 1 h", footerOf(NotificationsSamples.resultReady, now))
        assertEquals(
            "Míralas con ParcheU+ · sáb",
            footerOf(NotificationsSamples.lockedConnect, now)
        )
    }

    @Test
    fun introductionKindsShareTheirCategory() {
        // E6.2: propuesta (E3), simulación lista para quien presenta (E2) y 'Pedir presentación' (E1).
        listOf(
            NotificationKinds.INTRODUCTION_PROPOSAL,
            NotificationKinds.INTRODUCTION_READY,
            NotificationKinds.INTRODUCTION_REQUEST
        ).forEach { assertEquals("Presentación", categoryOf(it)) }
    }
}
