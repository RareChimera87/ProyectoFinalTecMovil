package me.santiagoreina.parcheu.feature.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import me.santiagoreina.parcheu.data.auth.TEST_LEGAL_VERSION
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La hoja de términos se comporta como modal: oculta el fondo a TalkBack y Atrás equivale a "Ahora no". */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class TermsSheetSemanticsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var declined = 0

    private fun show(login: LoginState) {
        composeRule.setContent {
            ParcheUTheme {
                OnboardingScreen(
                    state = OnboardingUiState(login),
                    pagerState = rememberPagerState(initialPage = 2) { ONBOARDING_PAGES },
                    termsUrl = "https://parcheu-dev.web.app/terminos",
                    privacyUrl = "https://parcheu-dev.web.app/privacidad",
                    onSkip = {},
                    onNext = {},
                    onContinueWithGoogle = {},
                    onAcceptTerms = {},
                    onDeclineTerms = { declined++ },
                    animateLoading = false
                )
            }
        }
    }

    @Test
    fun `con la hoja visible el fondo no es accesible`() {
        show(LoginState.TermsSheet(TEST_LEGAL_VERSION))
        composeRule.onNodeWithTag(OnboardingTags.TERMS_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(OnboardingTags.GOOGLE).assertDoesNotExist()
    }

    @Test
    fun `sin la hoja el fondo es accesible`() {
        show(LoginState.Idle)
        composeRule.onNodeWithTag(OnboardingTags.GOOGLE).assertIsDisplayed()
    }

    @Test
    fun `Atras con la hoja visible rechaza los terminos`() {
        show(LoginState.TermsSheet(TEST_LEGAL_VERSION))
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        assertEquals(1, declined)
    }
}
