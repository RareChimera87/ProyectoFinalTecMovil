package me.santiagoreina.parcheu.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric: las excepciones de Firebase usan clases de Android que el JVM puro no tiene. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FirebaseTokenProviderTest {
    @Test
    fun `devuelve el token y propaga forceRefresh`() = runTest {
        val flags = mutableListOf<Boolean>()
        val provider = FirebaseTokenProvider { force ->
            flags += force
            "tok-$force"
        }
        assertEquals("tok-false", provider.currentToken())
        assertEquals("tok-true", provider.refreshToken())
        assertEquals(listOf(false, true), flags)
    }

    @Test
    fun `sin usuario devuelve null`() = runTest {
        assertNull(FirebaseTokenProvider { null }.currentToken())
    }

    @Test
    fun `fallo de red se convierte en IOException y no en null`() = runTest {
        val provider = FirebaseTokenProvider { throw FirebaseNetworkException("sin red") }
        try {
            provider.currentToken()
            fail("Debía lanzar IOException")
        } catch (e: IOException) {
            assertTrue(e.cause is FirebaseNetworkException)
        }
    }

    @Test
    fun `demasiadas peticiones se convierte en IOException`() = runTest {
        val provider = FirebaseTokenProvider { throw FirebaseTooManyRequestsException("limite") }
        try {
            provider.refreshToken()
            fail("Debía lanzar IOException")
        } catch (e: IOException) {
            assertTrue(e.cause is FirebaseTooManyRequestsException)
        }
    }

    @Test
    fun `usuario o credencial invalidos devuelven null`() = runTest {
        val user = FirebaseTokenProvider {
            throw FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "deshabilitado")
        }
        val credential = FirebaseTokenProvider {
            throw FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "revocada")
        }
        assertNull(user.currentToken())
        assertNull(credential.currentToken())
    }

    @Test
    fun `la cancelacion se propaga`() = runTest {
        val provider = FirebaseTokenProvider { throw CancellationException("cancelado") }
        try {
            provider.currentToken()
            fail("Debía propagar CancellationException")
        } catch (_: CancellationException) {
        }
    }
}
