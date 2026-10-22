package me.santiagoreina.parcheu.screenshots

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.data.auth.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.onboarding.LoginState
import me.santiagoreina.parcheu.feature.onboarding.ONBOARDING_PAGES
import me.santiagoreina.parcheu.feature.onboarding.OnboardingScreen
import me.santiagoreina.parcheu.feature.onboarding.OnboardingUiState
import me.santiagoreina.parcheu.feature.splash.SplashScreen
import me.santiagoreina.parcheu.feature.splash.SplashUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de A1-A4, cargando, error y hoja de términos en ambos temas (ADR-0022): regresión contra las
 * PNG de app/src/test/screenshots/onboarding con `changeThreshold` 0,01. Grabar con
 * `./gradlew :app:recordRoborazziDevDebug`; la comparación con los mockups la hace I2 a mano.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class OnboardingScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { ParcheUTheme(darkTheme = dark) { content() } }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/onboarding/${name}_$theme.png",
            // Se crea dentro del sandbox de Robolectric: en el companion el runner parametrizado lo
            // inicializaría antes y RoborazziOptions falla fuera del sandbox.
            roborazziOptions =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            ),
        )
    }

    @Composable
    private fun Onboarding(page: Int, login: LoginState = LoginState.Idle) {
        OnboardingScreen(
            state = OnboardingUiState(login),
            pagerState = rememberPagerState(initialPage = page) { ONBOARDING_PAGES },
            termsUrl = "https://parcheu-dev.web.app/terminos",
            privacyUrl = "https://parcheu-dev.web.app/privacidad",
            onSkip = {},
            onNext = {},
            onContinueWithGoogle = {},
            onAcceptTerms = {},
            onDeclineTerms = {},
            animateLoading = false,
        )
    }

    @Test
    fun a1Splash() = capture("a1_splash") {
        SplashScreen(SplashUiState.Loading, onRetry = {}, animateLoading = false)
    }

    @Test
    fun a1SplashError() = capture("a1_splash_error") {
        SplashScreen(SplashUiState.Error(AppError.Network), onRetry = {}, animateLoading = false)
    }

    @Test
    fun a2() = capture("a2") { Onboarding(page = 0) }

    @Test
    fun a3() = capture("a3") { Onboarding(page = 1) }

    @Test
    fun a4() = capture("a4") { Onboarding(page = 2) }

    @Test
    fun a4Loading() = capture("a4_loading") { Onboarding(page = 2, login = LoginState.Loading) }

    @Test
    fun a4Error() = capture("a4_error") {
        Onboarding(page = 2, login = LoginState.Error(AppError.Network))
    }

    @Test
    fun a4TermsSheet() = capture("a4_terms_sheet") {
        Onboarding(page = 2, login = LoginState.TermsSheet(TEST_LEGAL_VERSION))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }
}
