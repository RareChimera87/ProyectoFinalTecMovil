package me.santiagoreina.parcheu.feature.results

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.data.results.fullReport
import me.santiagoreina.parcheu.data.results.lockedReport
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ResultsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun content(
        full: Boolean = false,
        isDemo: Boolean = false,
        decision: DecisionState = DecisionState.PENDING,
        unlockAvailable: Boolean = true
    ) = ResultsContent(
        otherLabel = "Alguien de la UIS",
        mode = Mode.ESTUDIO,
        isDemo = isDemo,
        plus = full,
        report = if (full) fullReport() else lockedReport(unlockAvailable),
        decision = decision,
        usedMonth = "octubre",
        resetMonth = "noviembre"
    )

    private fun show(content: ResultsContent, actions: ResultsActions = ResultsActions()) {
        composeRule.setContent {
            ParcheUTheme { ResultsScreen(ResultsUiState.Loaded(content), actions) }
        }
    }

    @Test
    fun lockedReportHasNoShareAndOffersTheMonthlyReport() {
        var unlocks = 0
        show(content(), ResultsActions(onUnlock = { unlocks++ }))
        composeRule.onNodeWithTag(ResultsTags.LOCKED).assertIsDisplayed()
        composeRule.onNodeWithTag(ResultsTags.SHARE).assertDoesNotExist()
        composeRule.onNodeWithTag(ResultsTags.UNLOCK).performScrollTo().performClick()
        assertEquals(1, unlocks)
        composeRule.onNodeWithText(
            "Tienes 1 informe completo gratis de octubre",
            substring = true
        ).assertExists()
    }

    @Test
    fun consumedReportOnlyOffersPlus() {
        show(content(unlockAvailable = false))
        composeRule.onNodeWithTag(ResultsTags.UNLOCK).assertDoesNotExist()
        composeRule.onNodeWithTag(ResultsTags.SEE_PLUS).assertExists()
        composeRule.onNodeWithText(
            "Ya usaste tu informe gratis de octubre. Vuelve en noviembre o míralo con ParcheU+."
        ).assertExists()
    }

    @Test
    fun demoBadgeStaysOnResults() {
        show(content(isDemo = true))
        composeRule.onNodeWithTag(ResultsTags.DEMO).assertExists()
        composeRule.onNodeWithText(DEMO_BADGE_TEXT).assertExists()
    }

    @Test
    fun fullReportShowsReasonsAndTranscriptLink() {
        var opened = 0
        show(content(full = true), ResultsActions(onOpenTranscript = { opened++ }))
        composeRule.onNodeWithText("Se dicen las cosas directo y sin drama").assertExists()
        composeRule.onNodeWithTag(ResultsTags.TRANSCRIPT_LINK).performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun footerSendsTheDecision() {
        val decisions = mutableListOf<Boolean>()
        show(
            content(),
            ResultsActions(onConnect = { decisions += true }, onDecline = {
                decisions +=
                    false
            })
        )
        composeRule.onNodeWithTag(ResultsTags.CONNECT).performClick()
        composeRule.onNodeWithTag(ResultsTags.DECLINE).performClick()
        assertEquals(listOf(true, false), decisions)
    }

    @Test
    fun waitingReplacesTheFooter() {
        show(content(decision = DecisionState.WAITING))
        composeRule.onNodeWithTag(ResultsTags.WAITING).assertExists()
        composeRule.onNodeWithTag(ResultsTags.FOOTER).assertDoesNotExist()
    }
}
