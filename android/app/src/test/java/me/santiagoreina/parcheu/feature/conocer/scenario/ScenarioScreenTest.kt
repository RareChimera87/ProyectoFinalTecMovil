package me.santiagoreina.parcheu.feature.conocer.scenario

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.ScenarioSessionDto
import me.santiagoreina.parcheu.contract.ScenarioSessionStatus
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScenarioScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun state(
        status: ScenarioSessionStatus,
        remaining: Long = 420,
        simulationId: String? = null,
        other: Boolean = false
    ) = ScenarioUiState(
        session = ScenarioSessionDto(
            status = status,
            simulationId = simulationId,
            title = "Planear el fin de semana",
            prompt = "Pónganse de acuerdo en un plan.",
            budgetCop = 80_000,
            endsAt = Instant.fromEpochSeconds(1_000),
            iAgreed = status == ScenarioSessionStatus.WAITING_OTHER,
            otherAgreed = other
        ),
        remainingSec = remaining,
        totalSec = 600
    )

    private fun show(state: ScenarioUiState, onAgree: () -> Unit = {}, onResults: () -> Unit = {}) {
        composeRule.setContent {
            ParcheUTheme {
                androidx.compose.foundation.layout.Column {
                    ScenarioPinnedCard(state)
                    ScenarioAgreementBar(state, onAgree, onResults)
                }
            }
        }
    }

    @Test
    fun cardShowsTitleTimerAndBudget() {
        show(state(ScenarioSessionStatus.OPEN))
        composeRule.onNodeWithText("Planear el fin de semana").assertIsDisplayed()
        composeRule.onNodeWithText("7:00").assertIsDisplayed()
        composeRule.onNodeWithText("Presupuesto $80.000").assertIsDisplayed()
    }

    @Test
    fun agreeButtonWorksOnlyWhileOpen() {
        var agreed = 0
        show(state(ScenarioSessionStatus.OPEN), onAgree = { agreed++ })
        composeRule.onNodeWithTag(ScenarioTags.AGREE).assertIsEnabled().performClick()
        assertEquals(1, agreed)
    }

    @Test
    fun agreeButtonDisabledWhenTimeIsUp() {
        show(state(ScenarioSessionStatus.OPEN, remaining = 0))
        composeRule.onNodeWithTag(ScenarioTags.AGREE).assertIsNotEnabled()
    }

    @Test
    fun waitingOtherReplacesTheButton() {
        show(state(ScenarioSessionStatus.WAITING_OTHER))
        composeRule.onNodeWithText(ScenarioStrings.WAITING_OTHER).assertIsDisplayed()
        composeRule.onNodeWithTag(ScenarioTags.AGREE).assertDoesNotExistForTest()
    }

    @Test
    fun otherAgreedIsAnnouncedWhileOpen() {
        show(state(ScenarioSessionStatus.OPEN, other = true))
        composeRule.onNodeWithText(ScenarioStrings.OTHER_AGREED).assertIsDisplayed()
    }

    @Test
    fun judgingAndDoneStates() {
        show(state(ScenarioSessionStatus.JUDGING))
        composeRule.onNodeWithText(ScenarioStrings.JUDGING).assertIsDisplayed()
    }

    @Test
    fun doneWithResultOffersResults() {
        var opened = 0
        show(state(ScenarioSessionStatus.DONE, simulationId = "s1"), onResults = { opened++ })
        composeRule.onNodeWithTag(ScenarioTags.RESULT).performClick()
        assertEquals(1, opened)
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistForTest() =
    assertDoesNotExist()
