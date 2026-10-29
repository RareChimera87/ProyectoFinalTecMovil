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

        @Volatile private var current = "viejo"

        override suspend fun currentToken(): String = current

        override suspend fun refreshToken(): String {
            refreshes++
            current = "nuevo-$refreshes"
            return current
        }

        override suspend fun currentUserId(): String? = null
    }

    private val tokens = CountingTokenProvider()
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server.start()
        client = createOkHttpClient(tokens, server.hostName, cleartextHosts = setOf(server.hostName))
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
        val anonymous = createOkHttpClient(NoSessionTokenProvider, server.hostName, cleartextHosts = setOf(server.hostName))
        server.enqueue(MockResponse(code = 200))

        anonymous.newCall(Request.Builder().url(server.url("/v1/health")).build()).execute().close()

        assertNull(server.takeRequest(1, TimeUnit.SECONDS)?.headers?.get("Authorization"))
    }

    @Test
    fun foreignHostNeverGetsTokenNorRefresh() {
        val other = MockWebServer()
        other.start()
        try {
            // El host de la API es otro: este servidor es un tercero (p. ej. fotos de Google).
            val apiOnly = createOkHttpClient(tokens, "api.parcheu.test")
            other.enqueue(MockResponse(code = 401))

            val response =
                apiOnly.newCall(Request.Builder().url(other.url("/foto.jpg")).build()).execute()

            assertEquals(401, response.code)
            assertEquals(0, tokens.refreshes)
            assertEquals(1, other.requestCount)
            assertNull(other.takeRequest(1, TimeUnit.SECONDS)?.headers?.get("Authorization"))
        } finally {
            other.close()
        }
    }

    @Test
    fun concurrent401sShareASingleRefresh() {
        server.dispatcher =
            object : mockwebserver3.Dispatcher() {
                override fun dispatch(request: mockwebserver3.RecordedRequest) =
                    if (request.headers["Authorization"] == "Bearer viejo") {
                        MockResponse(code = 401)
                    } else {
                        MockResponse(code = 200)
                    }
            }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(8)
        try {
            val codes = (1..8).map { pool.submit<Int> { get().use { it.code } } }.map { it.get() }
            assertEquals(List(8) { 200 }, codes)
            assertEquals(1, tokens.refreshes)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun cleartextToARealHostSendsNoTokenAndDoesNotRefresh() {
        // Sin el host en la lista de excepciones (como en una build normal), HTTP no lleva sesión.
        val strict = createOkHttpClient(tokens, server.hostName)
        server.enqueue(MockResponse(code = 401))

        val response = strict.newCall(Request.Builder().url(server.url("/v1/me")).build()).execute()

        assertEquals(401, response.code)
        assertEquals(0, tokens.refreshes)
        assertNull(server.takeRequest(1, TimeUnit.SECONDS)?.headers?.get("Authorization"))
    }

    @Test
    fun failedRefreshReleasesTheLockSoLaterRequestsRefreshAgain() {
        val flaky = object : TokenProvider {
            var calls = 0

            override suspend fun currentToken(): String = "viejo"

            override suspend fun refreshToken(): String {
                if (++calls == 1) error("sin red")
                return "nuevo"
            }

            override suspend fun currentUserId(): String? = null
        }
        val flakyClient = createOkHttpClient(flaky, server.hostName, cleartextHosts = setOf(server.hostName))
        server.enqueue(MockResponse(code = 401))
        runCatching {
            flakyClient.newCall(Request.Builder().url(server.url("/v1/me")).build()).execute().close()
        }
        server.enqueue(MockResponse(code = 401))
        server.enqueue(MockResponse(code = 200))

        // Si el primer fallo hubiera dejado el candado tomado, esta llamada se colgaría.
        val response = flakyClient.newCall(Request.Builder().url(server.url("/v1/me")).build()).execute()

        assertEquals(200, response.code)
        assertEquals(2, flaky.calls)
    }
}
