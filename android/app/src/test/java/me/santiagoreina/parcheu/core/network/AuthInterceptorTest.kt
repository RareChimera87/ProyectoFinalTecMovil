package me.santiagoreina.parcheu.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A-B1: el ID token solo viaja por HTTPS al host de la API; HTTP únicamente hacia el emulador. */
class AuthInterceptorTest {
    private val hosts = AuthInterceptor.EMULATOR_HOSTS

    private fun can(url: String, apiHost: String) = canCarryToken(url.toHttpUrl(), apiHost, hosts)

    @Test
    fun httpsToTheApiHostCarriesTheToken() {
        assertTrue(can("https://api.parcheu.test/v1/me", "api.parcheu.test"))
    }

    @Test
    fun cleartextToARealHostNeverCarriesTheToken() {
        assertFalse(can("http://api.parcheu.test/v1/me", "api.parcheu.test"))
        assertFalse(can("http://192.168.0.10:8090/v1/me", "192.168.0.10"))
    }

    @Test
    fun cleartextToTheEmulatorHostIsTheOnlyException() {
        assertTrue(can("http://10.0.2.2:8090/v1/me", "10.0.2.2"))
    }

    @Test
    fun otherHostsNeverCarryTheTokenEvenOverHttps() {
        assertFalse(can("https://lh3.googleusercontent.com/foto", "api.parcheu.test"))
    }
}
