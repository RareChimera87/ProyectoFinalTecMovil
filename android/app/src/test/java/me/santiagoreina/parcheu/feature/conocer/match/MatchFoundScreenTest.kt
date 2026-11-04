package me.santiagoreina.parcheu.feature.conocer.match

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class MatchFoundScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: MatchFoundUiState, onSelect: (MatchChoice) -> Unit = {}) {
        composeRule.setContent {
            ParcheUTheme {
                MatchFoundScreen(state, onSelect, {}, {}, {}, {})
            }
        }
    }

    @Test
    fun withoutScenarioOnlySimulationCardIsShown() {
        show(MatchFoundUiState.Loaded(testMatch(scenarioAvailable = false), MatchChoice.SIMULATION))

        composeRule.onNodeWithTag(MatchFoundTags.SIMULATION_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(MatchFoundTags.SCENARIO_CARD).assertDoesNotExist()
    }

    @Test
    fun withScenarioBothCardsAreShownAndSelectable() {
        var picked: MatchChoice? = null
        show(MatchFoundUiState.Loaded(testMatch(), MatchChoice.SCENARIO)) { picked = it }

        composeRule.onNodeWithTag(MatchFoundTags.SCENARIO_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(MatchFoundTags.SIMULATION_CARD).performClick()

        assertEquals(MatchChoice.SIMULATION, picked)
    }

    @Test
    fun demoProfileShowsBadge() {
        show(MatchFoundUiState.Loaded(testMatch(isDemo = true), MatchChoice.SCENARIO))

        composeRule.onNodeWithTag(MatchFoundTags.DEMO_BADGE).assertIsDisplayed()
    }

    @Test
    fun regularProfileHasNoBadge() {
        show(MatchFoundUiState.Loaded(testMatch(isDemo = false), MatchChoice.SCENARIO))

        composeRule.onNodeWithTag(MatchFoundTags.DEMO_BADGE).assertDoesNotExist()
    }
}
