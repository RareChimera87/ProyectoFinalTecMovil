package me.santiagoreina.parcheu.feature.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Los nombres del grafo y la afinidad del 84 % de A3-A4 son un ejemplo y la pantalla lo dice. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class ExampleLabelTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun show(page: Int) {
        composeRule.setContent {
            ParcheUTheme {
                OnboardingScreen(
                    state = OnboardingUiState(LoginState.Idle),
                    pagerState = rememberPagerState(initialPage = page) { ONBOARDING_PAGES },
                    termsUrl = "https://parcheu-dev.web.app/terminos",
                    privacyUrl = "https://parcheu-dev.web.app/privacidad",
                    onSkip = {},
                    onNext = {},
                    onContinueWithGoogle = {},
                    onAcceptTerms = {},
                    onDeclineTerms = {},
                    animateLoading = false
                )
            }
        }
    }

    @Test
    fun `la simulacion con 84 por ciento se rotula como ejemplo`() {
        show(page = 1)
        composeRule.onNodeWithText("Afinidad 84 %").assertIsDisplayed()
        composeRule.onNodeWithTag(OnboardingTags.EXAMPLE).assertIsDisplayed()
    }

    @Test
    fun `el grafo de ejemplo se rotula como ejemplo`() {
        show(page = 2)
        composeRule.onNodeWithTag(OnboardingTags.EXAMPLE).assertIsDisplayed()
    }

    @Test
    fun `la primera pagina no lleva rotulo`() {
        show(page = 0)
        composeRule.onNodeWithTag(OnboardingTags.EXAMPLE).assertDoesNotExist()
    }
}
