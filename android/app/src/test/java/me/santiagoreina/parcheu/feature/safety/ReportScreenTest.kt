package me.santiagoreina.parcheu.feature.safety

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import me.santiagoreina.parcheu.contract.ReportReason
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReportScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: ReportUiState, actions: ReportActions = ReportActions()) {
        composeRule.setContent { ParcheUTheme { ReportScreen(state, actions) } }
    }

    @Test
    fun h2ShowsThePromiseReasonsAndBothCheckboxes() {
        show(ReportUiState(FakeSafetyRepository.laura))
        composeRule.onNodeWithText("Reportar a Laura").assertIsDisplayed()
        composeRule.onNodeWithText(REPORT_PROMISE).assertIsDisplayed()
        composeRule.onNodeWithText("Habla de hacerse daño").assertIsDisplayed()
        composeRule.onNodeWithTag(SafetyTags.ATTACH).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SafetyTags.ALSO_BLOCK).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SafetyTags.SUBMIT).assertIsNotEnabled()
    }

    @Test
    fun pickingAReasonEnablesSubmit() {
        var picked: ReportReason? = null
        show(ReportUiState(FakeSafetyRepository.laura), ReportActions(onReason = { picked = it }))
        composeRule.onNodeWithTag(SafetyTags.REASON_PREFIX + "UNDERAGE").performClick()
        assertEquals(ReportReason.UNDERAGE, picked)
    }

    @Test
    fun submitIsEnabledWithAReason() {
        show(ReportUiState(FakeSafetyRepository.laura, reason = ReportReason.HARASSMENT))
        composeRule.onNodeWithTag(SafetyTags.SUBMIT).assertIsEnabled()
    }

    @Test
    fun sentWithSelfHarmShowsHelpLinesAndDialsOnTap() {
        var dialed: String? = null
        show(
            ReportUiState(
                FakeSafetyRepository.laura,
                reason = ReportReason.SELF_HARM,
                sent = ReportSent(FakeSafetyRepository.HELP, blocked = true)
            ),
            ReportActions(onCall = { dialed = it })
        )
        composeRule.onNodeWithText("Recibimos tu reporte").assertIsDisplayed()
        composeRule.onNodeWithTag(SafetyTags.HELP).assertIsDisplayed()
        composeRule.onNodeWithText("Línea 192, opción 4 · 192").performClick()
        assertEquals("192", dialed)
    }

    @Test
    fun sentWithoutHelpResourcesHasNoHelpCard() {
        show(
            ReportUiState(
                FakeSafetyRepository.laura,
                sent = ReportSent(emptyList(), blocked = false)
            )
        )
        composeRule.onNodeWithText("Recibimos tu reporte").assertIsDisplayed()
        composeRule.onNodeWithTag(SafetyTags.HELP).assertDoesNotExist()
    }
}
