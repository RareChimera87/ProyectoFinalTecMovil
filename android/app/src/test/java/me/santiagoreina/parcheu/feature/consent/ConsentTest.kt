package me.santiagoreina.parcheu.feature.consent

import app.cash.turbine.test
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.santiagoreina.parcheu.contract.ConsentKind
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.OnboardingStep
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.createRetrofit
import me.santiagoreina.parcheu.data.auth.SessionApi
import me.santiagoreina.parcheu.data.auth.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.data.auth.sessionDto
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** B4 (E2.5): 'Acepto' concede Amistad y Estudio (nunca Pareja); 'Ahora no' no escribe nada. */
class ConsentTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val server = MockWebServer().apply { start() }

    @After
    fun tearDown() {
        server.close()
    }

    private class FakeConsentRepository(var result: ApiResult<Unit> = ApiResult.Success(Unit)) : ConsentRepository {
        var grants = 0
        var revocations = 0

        override suspend fun grantOnboardingLearning(): ApiResult<Unit> {
            grants++
            return result
        }

        override suspend fun revokePartialGrants() {
            revocations++
        }
    }

    private fun apiRepo(): ApiConsentRepository {
        val retrofit = createRetrofit(server.url("/").toString(), OkHttpClient())
        return ApiConsentRepository(
            retrofit.create(ConsentApi::class.java),
            retrofit.create(SessionApi::class.java),
            ApiErrorMapper(),
        )
    }

    private fun bodyOf(request: mockwebserver3.RecordedRequest) =
        Json.parseToJsonElement(request.body!!.utf8()).jsonObject

    private fun enqueueSession() =
        server.enqueue(MockResponse(code = 200, body = ContractJson.encodeToString(sessionDto(OnboardingStep.DONE))))

    @Test
    fun acceptSendsAmistadAndEstudioWithTheCurrentLegalVersionAndNoPareja() = runTest {
        val retrofit = createRetrofit(server.url("/").toString(), OkHttpClient())
        val repo = ApiConsentRepository(
            retrofit.create(ConsentApi::class.java),
            retrofit.create(SessionApi::class.java),
            ApiErrorMapper(),
        )
        server.enqueue(MockResponse(code = 200, body = ContractJson.encodeToString(sessionDto(OnboardingStep.DONE))))
        repeat(2) { server.enqueue(MockResponse(code = 200, body = """{"items":[]}""")) }

        assertEquals(ApiResult.Success(Unit), repo.grantOnboardingLearning())

        assertEquals("/v1/session", server.takeRequest(1, TimeUnit.SECONDS)!!.url.encodedPath)
        val bodies = (1..2).map {
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("/v1/me/consent", request.url.encodedPath)
            Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        }
        assertEquals(
            listOf(ConsentKind.CHAT_LEARNING_AMISTAD.name, ConsentKind.CHAT_LEARNING_ESTUDIO.name),
            bodies.map { it.getValue("kind").jsonPrimitive.content },
        )
        assertTrue(bodies.all { it.getValue("granted").jsonPrimitive.content == "true" })
        assertTrue(bodies.all { it.getValue("textVersion").jsonPrimitive.content == TEST_LEGAL_VERSION })
        assertEquals(3, server.requestCount)
    }

    @Test
    fun partialFailureRevokesWhatWasAlreadyGranted() = runTest {
        val repo = apiRepo()
        enqueueSession()
        server.enqueue(MockResponse(code = 200, body = """{"items":[]}"""))
        server.enqueue(MockResponse(code = 500, body = "{}"))
        server.enqueue(MockResponse(code = 200, body = """{"items":[]}"""))

        assertTrue(repo.grantOnboardingLearning() is ApiResult.Failure)

        server.takeRequest(1, TimeUnit.SECONDS) // /v1/session
        val amistad = bodyOf(server.takeRequest(1, TimeUnit.SECONDS)!!)
        assertEquals("true", amistad.getValue("granted").jsonPrimitive.content)
        server.takeRequest(1, TimeUnit.SECONDS) // ESTUDIO falla
        val revoke = bodyOf(server.takeRequest(1, TimeUnit.SECONDS)!!)
        assertEquals(ConsentKind.CHAT_LEARNING_AMISTAD.name, revoke.getValue("kind").jsonPrimitive.content)
        assertEquals("false", revoke.getValue("granted").jsonPrimitive.content)
        assertEquals(4, server.requestCount)

        // Ya no queda nada por revocar: 'Ahora no' no envía nada más.
        repo.revokePartialGrants()
        assertEquals(4, server.requestCount)
    }

    @Test
    fun notNowRevokesGrantsThatCouldNotBeUndoneAfterAPartialFailure() = runTest {
        val repo = apiRepo()
        enqueueSession()
        server.enqueue(MockResponse(code = 200, body = """{"items":[]}"""))
        server.enqueue(MockResponse(code = 500, body = "{}")) // ESTUDIO falla
        server.enqueue(MockResponse(code = 500, body = "{}")) // la compensación también falla
        server.enqueue(MockResponse(code = 200, body = """{"items":[]}""")) // 'Ahora no'

        assertTrue(repo.grantOnboardingLearning() is ApiResult.Failure)
        repeat(4) { server.takeRequest(1, TimeUnit.SECONDS) }

        repo.revokePartialGrants()

        val revoke = bodyOf(server.takeRequest(1, TimeUnit.SECONDS)!!)
        assertEquals(ConsentKind.CHAT_LEARNING_AMISTAD.name, revoke.getValue("kind").jsonPrimitive.content)
        assertEquals("false", revoke.getValue("granted").jsonPrimitive.content)
        assertEquals(5, server.requestCount)
    }

    @Test
    fun acceptGrantsAndContinuesToB5() = runTest {
        val repo = FakeConsentRepository()
        val vm = ConsentViewModel(repo)
        vm.effects.test {
            vm.onAccept()
            assertEquals(ConsentEffect.Done, awaitItem())
        }
        assertEquals(1, repo.grants)
    }

    @Test
    fun notNowContinuesWithoutWritingConsents() = runTest {
        val repo = FakeConsentRepository()
        val vm = ConsentViewModel(repo)
        vm.effects.test {
            vm.onNotNow()
            assertEquals(ConsentEffect.Done, awaitItem())
        }
        assertEquals(0, repo.grants)
        assertEquals(1, repo.revocations)
    }

    @Test
    fun failedAcceptStaysOnB4WithTheError() = runTest {
        val vm = ConsentViewModel(FakeConsentRepository(ApiResult.Failure(AppError.Network)))
        vm.effects.test {
            vm.onAccept()
            expectNoEvents()
        }
        assertEquals(AppError.Network, vm.state.value.error)
    }
}
