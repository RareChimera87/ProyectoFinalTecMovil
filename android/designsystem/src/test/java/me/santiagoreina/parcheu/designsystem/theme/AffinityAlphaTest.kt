package me.santiagoreina.parcheu.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AffinityAlphaTest {
    @Test
    fun valuesFromTheTicket() {
        assertEquals(0.22f, affinityAlpha(50), 1e-6f)
        assertEquals(1.0f, affinityAlpha(95), 1e-6f)
        assertEquals(0.601f, affinityAlpha(72), 0.001f)
        assertEquals(0.22f, affinityAlpha(30), 1e-6f)
    }

    @Test
    fun clampsAboveNinetyFive() {
        assertEquals(1.0f, affinityAlpha(100), 1e-6f)
    }

    @Test
    fun isMonotonicBetweenFiftyAndNinetyFive() {
        val values = (50..95).map { affinityAlpha(it) }
        assertTrue(values.zipWithNext().all { (a, b) -> b > a })
    }
}
