package me.santiagoreina.parcheu.feature.splash

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import me.santiagoreina.parcheu.BuildConfig
import me.santiagoreina.parcheu.ParcheUApp
import me.santiagoreina.parcheu.contract.OnboardingStep
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.ui.stubTag
import me.santiagoreina.parcheu.data.auth.AuthRepository
import me.santiagoreina.parcheu.data.auth.FakeAuthRepository
import me.santiagoreina.parcheu.data.auth.sessionDto
import me.santiagoreina.parcheu.feature.onboarding.OnboardingTags
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.Home
import me.santiagoreina.parcheu.navigation.Onboarding
import me.santiagoreina.parcheu.navigation.Splash
import me.santiagoreina.parcheu.navigation.TwinQuestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Los tres caminos de A1 (sin sesión → A2; con sesión y gemelo → C1; con sesión sin gemelo → B1), A4
 * alcanzable tras cerrar sesión y los enlaces legales de A4 (E2.2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SplashNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController

    private fun launch(
        repository: AuthRepository,
        start: AppRoute = Splash,
        uriHandler: UriHandler = NoOpUriHandler
    ) {
        loadKoinModules(module { single<AuthRepository> { repository } })
        composeRule.setContent {
            navController = rememberNavController()
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                ParcheUApp(navController = navController, startDestination = start)
            }
        }
        composeRule.waitForIdle()
    }

    private fun session(step: OnboardingStep) = FakeAuthRepository(signedIn = true).apply {
        nextSessions(ApiResult.Success(sessionDto(step)))
    }

    private fun assertOnlyDestination(check: (androidx.navigation.NavDestination) -> Boolean) {
        val destination = navController.currentBackStackEntry?.destination
        assertTrue("destino actual: $destination", destination != null && check(destination))
        // replaceAll: el Splash no queda debajo (atrás cierra la app).
        assertEquals(null, navController.previousBackStackEntry)
    }

    @Test
    fun withoutSessionSplashOpensA2() {
        launch(FakeAuthRepository(signedIn = false))
        composeRule.onNodeWithTag(OnboardingTags.page(0)).assertIsDisplayed()
        assertOnlyDestination { it.hasRoute<Onboarding>() }
    }

    @Test
    fun withSessionAndTwinSplashOpensHome() {
        launch(session(OnboardingStep.DONE))
        composeRule.onNodeWithTag(stubTag(Home)).assertIsDisplayed()
        assertOnlyDestination { it.hasRoute<Home>() }
    }

    @Test
    fun withSessionWithoutTwinSplashOpensTwinQuestions() {
        launch(session(OnboardingStep.NEEDS_TWIN))
        // E2.4 reemplazó el stub de B1-B2 por la pantalla real.
        composeRule.onNodeWithTag("twinQuestions:screen").assertIsDisplayed()
        assertOnlyDestination { it.hasRoute<TwinQuestions>() }
    }

    @Test
    fun afterSignOutA4IsReachableWithGoogleButton() {
        launch(FakeAuthRepository(signedIn = false), start = Onboarding(page = 2))
        composeRule.onNodeWithTag(OnboardingTags.page(2)).assertIsDisplayed()
        composeRule.onNodeWithTag(OnboardingTags.GOOGLE).assertIsDisplayed()
    }

    @Test
    fun a4LegalLinksOpenBuildConfigUrls() {
        val opened = mutableListOf<String>()
        val handler =
            object : UriHandler {
                override fun openUri(uri: String) {
                    opened += uri
                }
            }
        launch(
            FakeAuthRepository(signedIn = false),
            start = Onboarding(page = 2),
            uriHandler = handler
        )

        val links =
            composeRule.onAllNodes(
                hasClickAction() and hasAnyAncestor(hasTestTag(OnboardingTags.LEGAL)),
                useUnmergedTree = true
            )
        links[0].performClick()
        links[1].performClick()

        assertEquals(listOf(BuildConfig.TERMS_URL, BuildConfig.PRIVACY_URL), opened)
    }
}

private object NoOpUriHandler : UriHandler {
    override fun openUri(uri: String) = Unit
}
