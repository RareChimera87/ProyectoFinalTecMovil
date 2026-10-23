package me.santiagoreina.parcheu.feature.simulation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** F5: 'No se descontó de tu cupo' aparece solo con `refunded = true`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class SimulationScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: SimulationUiState, onRetry: () -> Unit = {}) {
        composeRule.setContent {
            ParcheUTheme(darkTheme = true) {
                SimulationScreen(state, onClose = {
                }, onReload = {}, onRetry = onRetry, onHome = {}, animate = false)
            }
        }
    }

    @Test
    fun refundedShowsTheNotChargedMessage() {
        show(SimulationUiState.Failed(pausedAtScenario = 2, refunded = true, canRetry = true))
        composeRule.onNodeWithTag(SimulationTags.NOT_CHARGED).assertIsDisplayed()
        composeRule.onNodeWithText(NOT_CHARGED_TEXT).assertIsDisplayed()
    }

    @Test
    fun notRefundedHidesTheNotChargedMessage() {
        show(SimulationUiState.Failed(pausedAtScenario = 2, refunded = false, canRetry = true))
        composeRule.onNodeWithTag(SimulationTags.FAILED).assertIsDisplayed()
        composeRule.onNodeWithTag(SimulationTags.NOT_CHARGED).assertDoesNotExist()
    }

    @Test
    fun retryButtonCallsBack() {
        var retries = 0
        show(SimulationUiState.Failed(2, refunded = true, canRetry = true)) { retries++ }
        composeRule.onNodeWithTag(SimulationTags.RETRY).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun withoutRetryTheButtonIsHidden() {
        show(SimulationUiState.Failed(2, refunded = true, canRetry = false))
        composeRule.onNodeWithTag(SimulationTags.RETRY).assertDoesNotExist()
        composeRule.onNodeWithTag(SimulationTags.HOME).assertIsDisplayed()
    }
}
