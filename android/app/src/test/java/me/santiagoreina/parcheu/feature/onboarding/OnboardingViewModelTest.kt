package me.santiagoreina.parcheu.feature.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.OnboardingStep
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.auth.FakeAuthRepository
import me.santiagoreina.parcheu.data.auth.SignInResult
import me.santiagoreina.parcheu.data.auth.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.data.auth.sessionDto
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.navigation.BasicProfile
import me.santiagoreina.parcheu.navigation.Home
import me.santiagoreina.parcheu.navigation.Modes
import me.santiagoreina.parcheu.navigation.TwinQuestions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Login de A4 con [FakeAuthRepository] (criterios de E2.2). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun loggedInWith(step: OnboardingStep) = FakeAuthRepository().apply {
        signInResult = SignInResult.Success(sessionDto(step))
        nextSessions(ApiResult.Success(sessionDto(step)))
    }

    @Test
    fun successNavigatesAccordingToOnboardingStep() = runTest {
        val expected =
            mapOf(
                OnboardingStep.NEEDS_PROFILE to BasicProfile(),
                OnboardingStep.NEEDS_TWIN to TwinQuestions,
                OnboardingStep.NEEDS_MODES to Modes(),
                OnboardingStep.DONE to Home
            )
        expected.forEach { (step, route) ->
            val viewModel = OnboardingViewModel(loggedInWith(step))
            viewModel.effects.test {
                viewModel.onContinueWithGoogle(context)
                assertEquals(OnboardingEffect.Navigate(route), awaitItem())
            }
            assertEquals(LoginState.Idle, viewModel.state.value.login)
        }
    }

    @Test
    fun cancellationReturnsToA4WithoutError() = runTest {
        val repository = FakeAuthRepository().apply { signInResult = SignInResult.Cancelled }
        val viewModel = OnboardingViewModel(repository)

        viewModel.effects.test {
            viewModel.onContinueWithGoogle(context)
            expectNoEvents()
        }
        assertEquals(LoginState.Idle, viewModel.state.value.login)
        assertEquals(listOf("signIn"), repository.calls)
    }

    @Test
    fun networkErrorShowsMessageAndRetrySucceeds() = runTest {
        val repository = FakeAuthRepository().apply {
            signInResult =
                SignInResult.Failure(AppError.Network)
        }
        val viewModel = OnboardingViewModel(repository)

        viewModel.onContinueWithGoogle(context)
        assertEquals(LoginState.Error(AppError.Network), viewModel.state.value.login)
        assertEquals(
            "No hay conexión. Revisa tu internet e inténtalo de nuevo.",
            loginErrorMessage(AppError.Network)
        )

        repository.signInResult = SignInResult.Success(sessionDto(OnboardingStep.NEEDS_TWIN))
        viewModel.effects.test {
            viewModel.onContinueWithGoogle(context)
            assertEquals(OnboardingEffect.Navigate(TwinQuestions), awaitItem())
        }
    }

    @Test
    fun retryAfterSessionFailureDoesNotReopenAccountPicker() = runTest {
        val repository =
            FakeAuthRepository().apply {
                signInResult = SignInResult.Failure(AppError.Network)
                // Firebase ya inició sesión pero POST /v1/session falló.
                signedIn = false
            }
        val viewModel = OnboardingViewModel(repository)
        viewModel.onContinueWithGoogle(context)

        repository.signedIn = true
        repository.nextSessions(ApiResult.Success(sessionDto(OnboardingStep.DONE)))
        viewModel.effects.test {
            viewModel.onContinueWithGoogle(context)
            assertEquals(OnboardingEffect.Navigate(Home), awaitItem())
        }
        assertEquals(listOf("signIn", "session"), repository.calls)
    }

    @Test
    fun needsTermsAcceptsExactlyOnceBeforeNavigating() = runTest {
        val repository =
            FakeAuthRepository().apply {
                signInResult = SignInResult.Success(sessionDto(OnboardingStep.NEEDS_TERMS))
                nextSessions(ApiResult.Success(sessionDto(OnboardingStep.NEEDS_PROFILE)))
            }
        val viewModel = OnboardingViewModel(repository)

        viewModel.effects.test {
            viewModel.onContinueWithGoogle(context)
            assertEquals(OnboardingEffect.Navigate(BasicProfile()), awaitItem())
            // Al navegar, la aceptación ya se envió una sola vez y con la versión vigente.
            assertEquals(listOf("signIn", "terms:$TEST_LEGAL_VERSION", "session"), repository.calls)
        }
        assertEquals(1, repository.calls.count { it.startsWith("terms:") })
    }

    @Test
    fun termsFailureShowsErrorAndDoesNotNavigate() = runTest {
        val repository =
            FakeAuthRepository().apply {
                signInResult = SignInResult.Success(sessionDto(OnboardingStep.NEEDS_TERMS))
                termsResult = ApiResult.Failure(AppError.Network)
                nextSessions(ApiResult.Success(sessionDto(OnboardingStep.NEEDS_TERMS)))
            }
        val viewModel = OnboardingViewModel(repository)

        viewModel.effects.test {
            viewModel.onContinueWithGoogle(context)
            expectNoEvents()
        }
        assertEquals(LoginState.Error(AppError.Network), viewModel.state.value.login)
    }

    @Test
    fun existingSessionWithOutdatedTermsShowsSheetAndAccepts() = runTest {
        val newVersion = "2026-12-01"
        val repository =
            FakeAuthRepository(signedIn = true).apply {
                nextSessions(
                    ApiResult.Success(sessionDto(OnboardingStep.NEEDS_TERMS, newVersion)),
                    ApiResult.Success(sessionDto(OnboardingStep.DONE, newVersion))
                )
            }
        val viewModel = OnboardingViewModel(repository)
        assertEquals(LoginState.TermsSheet(newVersion), viewModel.state.value.login)
        assertEquals(listOf("session"), repository.calls)

        viewModel.effects.test {
            viewModel.onAcceptTerms()
            assertEquals(OnboardingEffect.Navigate(Home), awaitItem())
        }
        assertEquals(listOf("session", "terms:$newVersion", "session"), repository.calls)
    }

    @Test
    fun retryWithPreexistingSessionShowsSheetInsteadOfAcceptingTerms() = runTest {
        val newVersion = "2026-12-01"
        val repository =
            FakeAuthRepository(signedIn = true).apply {
                nextSessions(
                    ApiResult.Failure(AppError.Network),
                    ApiResult.Success(sessionDto(OnboardingStep.NEEDS_TERMS, newVersion))
                )
            }
        val viewModel = OnboardingViewModel(repository)
        assertEquals(LoginState.Error(AppError.Network), viewModel.state.value.login)

        viewModel.onContinueWithGoogle(context)

        assertEquals(LoginState.TermsSheet(newVersion), viewModel.state.value.login)
        assertEquals(0, repository.calls.count { it.startsWith("terms:") })
    }

    @Test
    fun decliningTermsSignsOut() = runTest {
        val repository =
            FakeAuthRepository(signedIn = true).apply {
                nextSessions(
                    ApiResult.Success(sessionDto(OnboardingStep.NEEDS_TERMS, "2026-12-01"))
                )
            }
        val viewModel = OnboardingViewModel(repository)

        viewModel.onDeclineTerms()

        assertEquals(LoginState.Idle, viewModel.state.value.login)
        assertEquals(false, repository.signedIn)
        assertEquals(listOf("session", "signOut"), repository.calls)
    }

    @Test
    fun unauthorizedSessionSignsOutAndShowsError() = runTest {
        val repository =
            FakeAuthRepository().apply {
                signInResult = SignInResult.Failure(AppError.Unauthorized)
            }
        val viewModel = OnboardingViewModel(repository)

        viewModel.onContinueWithGoogle(context)

        assertEquals(LoginState.Error(AppError.Unauthorized), viewModel.state.value.login)
        assertEquals(listOf("signIn", "signOut"), repository.calls)
    }
}
