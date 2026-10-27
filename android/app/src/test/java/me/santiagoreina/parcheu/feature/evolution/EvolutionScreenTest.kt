package me.santiagoreina.parcheu.feature.evolution

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** D9 (E6.5): meses, momentos, estado de poco historial y bloqueo sin datos reales. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class EvolutionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        state: EvolutionUiState,
        callbacks: EvolutionCallbacks = EvolutionCallbacks()
    ) = composeRule.setContent {
        ParcheUTheme(darkTheme = true) { EvolutionScreen(state, callbacks, animate = false) }
    }

    @Test
    fun historyListsMonthsAndMoments() {
        var tapped: String? = null
        show(
            EvolutionUiState.Content(EvolutionSamples.full),
            EvolutionCallbacks(onMonth = {
                tapped =
                    it
            })
        )
        composeRule.onNodeWithText("Julio").assertIsDisplayed()
        composeRule.onNodeWithText("4 conexiones").assertIsDisplayed()
        composeRule.onNodeWithTag(EvolutionTags.month("2026-09")).performScrollTo().performClick()
        assertEquals("2026-09", tapped)
        composeRule.onNodeWithText("MOMENTOS").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "Jul · Tu primera conexión"
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun selectedMonthShowsItsDetail() {
        show(EvolutionUiState.Content(EvolutionSamples.full, selected = "2026-10"))
        composeRule.onNodeWithTag(EvolutionTags.DETAIL).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "3 conexiones nuevas · 12 personas en tu parche"
        ).assertIsDisplayed()
    }

    @Test
    fun tooEarlyExplainsAndHasNoMonthCards() {
        show(EvolutionUiState.TooEarly(EvolutionSamples.tooEarly))
        composeRule.onNodeWithTag(EvolutionTags.TOO_EARLY).assertIsDisplayed()
        composeRule.onAllNodes(hasTestTag(EvolutionTags.MONTHS)).assertCountEquals(0)
    }

    @Test
    fun lockedShowsNoRealData() {
        var unlocked = 0
        show(EvolutionUiState.Locked, EvolutionCallbacks(onUnlock = { unlocked++ }))
        composeRule.onNodeWithTag(EvolutionTags.LOCKED).assertIsDisplayed()
        composeRule.onAllNodesWithText("MOMENTOS").assertCountEquals(0)
        composeRule.onNodeWithText("Ver ParcheU+").performClick()
        assertEquals(1, unlocked)
    }
}
