package me.santiagoreina.parcheu.core.network

import java.io.IOException
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Un fallo transitorio al obtener el token llega como IOException (AppError.Network), no como 401. */
class TokenNetworkFailureTest {
    private val server = MockWebServer()

    private class FailingProvider : TokenProvider {
        override suspend fun currentToken(): String? = throw IOException("sin red")

        override suspend fun refreshToken(): String? = throw IOException("sin red")

        override suspend fun currentUserId(): String? = null
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `el interceptor propaga IOException y no envia la peticion sin token`() {
        server.enqueue(MockResponse.Builder().code(200).build())
        server.start()
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(FailingProvider(), server.hostName))
            .build()
        try {
            client.newCall(Request.Builder().url(server.url("/v1/session")).build()).execute()
            fail("Debía lanzar IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("sin red"))
        }
        assertEquals(0, server.requestCount)
    }
}
