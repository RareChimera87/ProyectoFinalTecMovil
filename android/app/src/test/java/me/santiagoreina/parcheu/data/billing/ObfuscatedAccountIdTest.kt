package me.santiagoreina.parcheu.data.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** E7.2: `obfuscatedAccountId = sha256(userId)` en hex minúscula (arquitectura §9). */
class ObfuscatedAccountIdTest {
    @Test
    fun matchesKnownSha256Vector() {
        // Vector de FIPS 180-2 para "abc".
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            obfuscatedAccountId("abc")
        )
    }

    @Test
    fun hashesTheApiUserIdAsLowercaseHex() {
        val userId = "3f0e8c1a-6b2d-4e9f-9a7c-1d2e3f4a5b6c"

        val id = obfuscatedAccountId(userId)

        // Calculado aparte con `printf %s <userId> | sha256sum`.
        assertEquals("53d33fd873210cc1f92da498b6ac2ef40b5088984ad30370081d475f1614917f", id)
        assertTrue(id.matches(Regex("[0-9a-f]{64}")))
        assertFalse(id.contains(userId))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankUserId() {
        obfuscatedAccountId(" ")
    }
}
