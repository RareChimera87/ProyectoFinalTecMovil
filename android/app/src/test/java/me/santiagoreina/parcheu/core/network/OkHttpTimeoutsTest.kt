package me.santiagoreina.parcheu.core.network

import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OkHttpTimeoutsTest {
    @Test
    fun clientUsesTheDocumentedTimeouts() {
        val client = createOkHttpClient(NoSessionTokenProvider, "api.parcheu.test")

        assertEquals(10_000, client.connectTimeoutMillis)
        assertEquals(30_000, client.readTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
        assertEquals(60_000, client.callTimeoutMillis)
    }

    @Test
    fun slowClientRaisesOnlyReadWriteAndCallTimeouts() {
        val base = createOkHttpClient(NoSessionTokenProvider, "api.parcheu.test")

        val slow = slowClientOf(base)

        assertEquals(10_000, slow.connectTimeoutMillis)
        assertEquals(120_000, slow.readTimeoutMillis)
        assertEquals(120_000, slow.writeTimeoutMillis)
        assertEquals(180_000, slow.callTimeoutMillis)
        // El cliente base no cambia y ambos comparten pool, interceptores y autenticador.
        assertEquals(30_000, base.readTimeoutMillis)
        assertSame(base.connectionPool, slow.connectionPool)
        assertEquals(base.interceptors.size, slow.interceptors.size)
        assertSame(base.authenticator, slow.authenticator)
    }

    @Test
    fun slowClientStillSendsTheSession() {
        val server = MockWebServer()
        server.start()
        try {
            val slow = slowClientOf(createOkHttpClient(object : TokenProvider by NoSessionTokenProvider {
                override suspend fun currentToken() = "tok"
            }, server.hostName, cleartextHosts = setOf(server.hostName)))
            server.enqueue(MockResponse(code = 200))

            slow.newCall(Request.Builder().url(server.url("/v1/me/export")).build()).execute().close()

            assertEquals("Bearer tok", server.takeRequest(1, TimeUnit.SECONDS)?.headers?.get("Authorization"))
        } finally {
            server.close()
        }
    }
}
