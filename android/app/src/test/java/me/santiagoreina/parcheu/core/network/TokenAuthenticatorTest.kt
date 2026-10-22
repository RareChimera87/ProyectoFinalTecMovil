package me.santiagoreina.parcheu.core.network

import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TokenAuthenticatorTest {
    private val server = MockWebServer()

    private class CountingTokenProvider : TokenProvider {
        var refreshes = 0

        override suspend fun currentToken(): String = "viejo"

        override suspend fun refreshToken(): String {
            refreshes++
            return "nuevo-$refreshes"
        }
    }

    private val tokens = CountingTokenProvider()
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server.start()
        client = createOkHttpClient(tokens)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun get() =
        client.newCall(Request.Builder().url(server.url("/v1/me")).build()).execute()

    @Test
    fun on401RefreshesTokenOnceAndRetries() {
        server.enqueue(MockResponse(code = 401))
        server.enqueue(MockResponse(code = 200, body = "{}"))

        val response = get()

        assertEquals(200, response.code)
        assertEquals(1, tokens.refreshes)
        assertEquals(
            "Bearer viejo",
            server.takeRequest(1, TimeUnit.SECONDS)?.headers?.get("Authorization")
        )
        assertEquals(
            "Bearer nuevo-1",
            server.takeRequest(1, TimeUnit.SECONDS)?.headers?.get("Authorization")
        )
        assertEquals(2, server.requestCount)
    }

    @Test
    fun secondConsecutive401GivesUpWithoutRefreshingAgain() {
        server.enqueue(MockResponse(code = 401))
        server.enqueue(MockResponse(code = 401))
        server.enqueue(MockResponse(code = 200))

        val response = get()

        assertEquals(401, response.code)
        assertEquals(1, tokens.refreshes)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun withoutSessionNoAuthorizationHeaderIsSent() {
        val anonymous = createOkHttpClient(NoSessionTokenProvider)
        server.enqueue(MockResponse(code = 200))

        anonymous.newCall(Request.Builder().url(server.url("/v1/health")).build()).execute().close()

        assertNull(server.takeRequest(1, TimeUnit.SECONDS)?.headers?.get("Authorization"))
    }
}
