package me.santiagoreina.parcheu.data.auth

import android.util.Base64
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoogleNonceTest {
    private fun jwt(payload: String): String {
        fun enc(s: String) = Base64.encodeToString(
            s.toByteArray(),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        return "${enc("""{"alg":"RS256"}""")}.${enc(payload)}.firma"
    }

    @Test
    fun hashedIsTheHexSha256OfTheRawNonce() {
        val nonce = GoogleNonce.generate()

        assertEquals(64, nonce.raw.length)
        val expected = MessageDigest.getInstance("SHA-256").digest(nonce.raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, nonce.hashed)
        assertNotEquals(nonce.raw, nonce.hashed)
    }

    @Test
    fun everyAttemptGetsADifferentNonce() {
        assertNotEquals(GoogleNonce.generate().hashed, GoogleNonce.generate().hashed)
    }

    @Test
    fun tokenWithTheSameNonceMatches() {
        val nonce = GoogleNonce.generate()

        assertTrue(nonce.matches(jwt("""{"sub":"1","nonce":"${nonce.hashed}"}""")))
    }

    @Test
    fun tokenFromAnotherRequestIsRejected() {
        val nonce = GoogleNonce.generate()

        assertFalse(nonce.matches(jwt("""{"sub":"1","nonce":"${GoogleNonce.generate().hashed}"}""")))
    }

    @Test
    fun tokenWithoutTheClaimOrNotAJwtIsAcceptedSoLoginDoesNotBreak() {
        val nonce = GoogleNonce.generate()

        assertTrue(nonce.matches(jwt("""{"sub":"1"}""")))
        assertTrue(nonce.matches("no-es-un-jwt"))
        assertNull(GoogleNonce.nonceClaimOf("a.%%%.c"))
    }
}
