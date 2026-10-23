package me.santiagoreina.parcheu.feature.splash

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.OnboardingStep
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.auth.FakeAuthRepository
import me.santiagoreina.parcheu.data.auth.sessionDto
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.navigation.BasicProfile
import me.santiagoreina.parcheu.navigation.Home
import me.santiagoreina.parcheu.navigation.Onboarding
import me.santiagoreina.parcheu.navigation.TwinQuestions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** A1 decide el destino según la sesión (E2.2). */
class SplashViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun withoutSessionGoesToFirstOnboardingPage() = runTest {
        val viewModel = SplashViewModel(FakeAuthRepository(signedIn = false))
        viewModel.destination.test { assertEquals(Onboarding(page = 0), awaitItem()) }
    }

    @Test
    fun sessionWithTwinGoesHome() = runTest {
        val repository =
            FakeAuthRepository(signedIn = true).apply {
                nextSessions(ApiResult.Success(sessionDto(OnboardingStep.DONE)))
            }
        SplashViewModel(repository).destination.test { assertEquals(Home, awaitItem()) }
    }

    @Test
    fun sessionWithoutTwinGoesToTwinQuestions() = runTest {
        val repository =
            FakeAuthRepository(signedIn = true).apply {
                nextSessions(ApiResult.Success(sessionDto(OnboardingStep.NEEDS_TWIN)))
            }
        SplashViewModel(repository).destination.test { assertEquals(TwinQuestions, awaitItem()) }
    }

    @Test
    fun pendingProfileAndTermsGoToTheirScreens() = runTest {
        val profile =
            FakeAuthRepository(signedIn = true).apply {
                nextSessions(ApiResult.Success(sessionDto(OnboardingStep.NEEDS_PROFILE)))
            }
        SplashViewModel(profile).destination.test { assertEquals(BasicProfile(), awaitItem()) }
        val terms =
            FakeAuthRepository(signedIn = true).apply {
                nextSessions(ApiResult.Success(sessionDto(OnboardingStep.NEEDS_TERMS)))
            }
        SplashViewModel(terms).destination.test { assertEquals(Onboarding(page = 2), awaitItem()) }
    }

    @Test
    fun networkErrorShowsRetry() = runTest {
        val repository =
            FakeAuthRepository(signedIn = true).apply {
                nextSessions(
                    ApiResult.Failure(AppError.Network),
                    ApiResult.Success(sessionDto(OnboardingStep.DONE))
                )
            }
        val viewModel = SplashViewModel(repository)
        assertEquals(SplashUiState.Error(AppError.Network), viewModel.state.value)

        viewModel.destination.test {
            viewModel.onRetry()
            assertEquals(Home, awaitItem())
        }
    }

    @Test
    fun revokedSessionSignsOutAndStartsOnboarding() = runTest {
        val repository =
            FakeAuthRepository(signedIn = true).apply {
                nextSessions(ApiResult.Failure(AppError.Unauthorized))
            }
        SplashViewModel(repository).destination.test {
            assertEquals(Onboarding(page = 0), awaitItem())
        }
        assertEquals(listOf("session", "signOut"), repository.calls)
    }
}
