package me.santiagoreina.parcheu.feature.parche.fof

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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

/** D6/D7 (E6.3): la versión gratis no muestra candidatos; ParcheU+ los lista y pide presentación. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class FofScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: FofUiState, callbacks: FofCallbacks = FofCallbacks()) =
        composeRule.setContent {
            ParcheUTheme(darkTheme = true) {
                FofScreen(state, callbacks, animate = false)
            }
        }

    @Test
    fun freeSeesLockWithCountAndNoCandidates() {
        var unlocked = 0
        show(FofUiState.Locked(12), FofCallbacks(onUnlock = { unlocked++ }))
        composeRule.onNodeWithTag(FofTags.LOCKED).assertIsDisplayed()
        composeRule.onNodeWithText("Hay 12 personas a 2 pasos de ti").assertIsDisplayed()
        composeRule.onAllNodes(hasTestTag(FofTags.LIST)).assertCountEquals(0)
        composeRule.onAllNodesWithText("Pedir presentación").assertCountEquals(0)
        composeRule.onNodeWithText("Ver ParcheU+").performClick()
        assertEquals(1, unlocked)
    }

    @Test
    fun plusSeesCandidatesWithEstimatedAffinityAndNoNameForAnonymous() {
        show(FofUiState.Unlocked(FofSamples.plus))
        composeRule.onNodeWithText("Alguien · 87 % afín").assertIsDisplayed()
        composeRule.onNodeWithText("Felipe R. · 84 % afín").assertIsDisplayed()
        // El anónimo nunca muestra la universidad ni un nombre en la tarjeta.
        composeRule.onAllNodesWithText("Alguien de la UIS · 87 % afín").assertCountEquals(0)
        composeRule.onNodeWithText("2 amigos en común · afinidad estimada").assertIsDisplayed()
    }

    @Test
    fun requestIntroductionSendsTheAnonId() {
        val sent = mutableListOf<String>()
        show(
            FofUiState.Unlocked(FofSamples.plus),
            FofCallbacks(onRequestIntroduction = {
                sent += it
            })
        )
        composeRule.onAllNodesWithText("Pedir presentación")[0].performScrollTo().performClick()
        assertEquals(listOf(FofSamples.candidates[0].anonId), sent)
    }

    @Test
    fun requestedCandidateIsDisabledAndMessageShows() {
        val anonId = FofSamples.candidates[0].anonId
        show(
            FofUiState.Unlocked(
                FofSamples.plus,
                requested = setOf(anonId),
                message = FofViewModel.REQUESTED_MESSAGE
            )
        )
        composeRule.onNodeWithText("Pedida").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(FofTags.MESSAGE).assertIsDisplayed()
    }

    @Test
    fun plusWithoutCandidatesShowsEmpty() {
        show(FofUiState.Unlocked(FofSamples.plusEmpty))
        composeRule.onNodeWithTag(FofTags.EMPTY).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun errorOffersRetry() {
        var retried = 0
        show(
            FofUiState.Error(me.santiagoreina.parcheu.core.error.AppError.Network),
            FofCallbacks(onRetry = { retried++ })
        )
        composeRule.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun anonymousLabelsAreDetected() {
        assertEquals(listOf(true, false, true), FofSamples.candidates.map { it.isAnonymous })
    }
}
