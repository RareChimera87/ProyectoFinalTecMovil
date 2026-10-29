package me.santiagoreina.parcheu.data.auth

import android.content.Context
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.OnboardingStep
import me.santiagoreina.parcheu.contract.SessionDto
import me.santiagoreina.parcheu.core.network.ApiResult

/** Versión del texto legal que usan las pruebas. */
const val TEST_LEGAL_VERSION = "2026-10-29"

fun sessionDto(step: OnboardingStep, version: String = TEST_LEGAL_VERSION) = SessionDto(
    me = MeDto(
        id = "u-ana",
        email = "ana@example.com",
        displayName = "Ana",
        createdAt = Instant.fromEpochSeconds(0)
    ),
    onboarding = step,
    legalTextVersion = version
)

/**
 * [AuthRepository] en memoria. [calls] registra el orden de las llamadas ("signIn", "session",
 * "terms:<versión>", "signOut") para verificar, por ejemplo, que los términos se aceptan antes de navegar.
 */
class FakeAuthRepository(var signedIn: Boolean = false) : AuthRepository {
    var signInResult: SignInResult = SignInResult.Cancelled

    /** Respuestas de `POST /v1/session` en orden; la última se repite. */
    val sessionResults = ArrayDeque<ApiResult<SessionDto>>()

    var termsResult: ApiResult<Unit> = ApiResult.Success(Unit)

    val calls = mutableListOf<String>()

    fun nextSessions(vararg results: ApiResult<SessionDto>) {
        sessionResults.clear()
        sessionResults.addAll(results)
    }

    /** Si no es `null`, [signOut] espera aquí con la sesión ya marcada como cerrándose (red lenta). */
    var signOutGate: CompletableDeferred<Unit>? = null

    private val signingOutState = MutableStateFlow(false)

    /** `true` mientras [signOut] está en curso, como `SignOutFlow.inProgress`. */
    val signingOut: Boolean get() = signingOutState.value

    override fun hasSession(): Boolean = signedIn && !signingOut

    /** Como `FirebaseAuthRepository`: espera a que termine un [signOut] en curso antes de "abrir el selector". */
    override suspend fun signInWithGoogle(activityContext: Context): SignInResult {
        signingOutState.first { !it }
        calls += "signIn"
        val result = signInResult
        if (result is SignInResult.Success) signedIn = true
        return result
    }

    override suspend fun refreshSession(): ApiResult<SessionDto> {
        calls += "session"
        return if (sessionResults.size > 1) sessionResults.removeFirst() else sessionResults.first()
    }

    override suspend fun acceptTerms(textVersion: String): ApiResult<Unit> {
        calls += "terms:$textVersion"
        return termsResult
    }

    override suspend fun signOut() {
        calls += "signOut"
        signingOutState.value = true
        try {
            signOutGate?.await()
            signedIn = false
        } finally {
            signingOutState.value = false
        }
    }
}
