package me.santiagoreina.parcheu.feature.parche

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.time.Instant
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Bloqueo para gratis, acciones de la hoja D2 por variante y reintento de D5 (E3.4). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class ParcheScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-11-04T11:00:00Z")

    private fun show(state: ParcheUiState, callbacks: ParcheCallbacks = ParcheCallbacks()) =
        composeRule.setContent {
            ParcheUTheme(darkTheme = true) {
                ParcheScreen(state, callbacks, now = now, animate = false)
            }
        }

    private fun sheet(id: String) = ParcheSamples.nodes.first { it.id == id }.let {
        NodeSheet(it, ParcheSamples.detail(it), loadingDetail = false)
    }

    @Test
    fun freeUserSeesLockedBannerAndPlusUserDoesNot() {
        show(ParcheUiState.Loaded(ParcheSamples.content))
        composeRule.onNode(hasTestTag(PARCHE_BANNER_TAG)).assertIsDisplayed()
    }

    @Test
    fun plusUserHasNoBanner() {
        show(ParcheUiState.Loaded(ParcheSamples.content.copy(plus = true)))
        composeRule.onAllNodes(hasTestTag(PARCHE_BANNER_TAG)).assertCountEquals(0)
    }

    @Test
    fun connectedSheetOffersSimulateAndChat() {
        var simulated = 0
        var chatted = 0
        show(
            ParcheUiState.Loaded(ParcheSamples.content, sheet = sheet("and")),
            ParcheCallbacks(onSimulateAgain = { simulated++ }, onChat = { chatted++ })
        )
        composeRule.onNodeWithText("Simular de nuevo").performClick()
        composeRule.onNodeWithText("Chatear").performClick()
        assertEquals(1, simulated)
        assertEquals(1, chatted)
        composeRule.onNodeWithText("Última simulación · hace 3 días").assertIsDisplayed()
    }

    @Test
    fun connectedSheetWithoutChatIdHidesChat() {
        val node = ParcheSamples.nodes.first { it.id == "and" }
        val withoutChat = ParcheSamples.detail(node).copy(chatId = null)
        show(
            ParcheUiState.Loaded(
                ParcheSamples.content,
                sheet = NodeSheet(node, withoutChat, loadingDetail = false)
            )
        )
        composeRule.onNodeWithText("Simular de nuevo").assertIsDisplayed()
        composeRule.onAllNodesWithText("Chatear").assertCountEquals(0)
    }

    @Test
    fun simulatedSheetHasNoChatAndPendingOnlyShowsResult() {
        show(ParcheUiState.Loaded(ParcheSamples.content, sheet = sheet("dan")))
        composeRule.onNodeWithText("Simular de nuevo").assertIsDisplayed()
        composeRule.onAllNodesWithText("Chatear").assertCountEquals(0)
    }

    @Test
    fun pendingSheetOffersOnlyViewResult() {
        show(ParcheUiState.Loaded(ParcheSamples.content, sheet = sheet("mat")))
        composeRule.onNodeWithText("Ver resultado").assertIsDisplayed()
        composeRule.onAllNodesWithText("Simular de nuevo").assertCountEquals(0)
        composeRule.onAllNodesWithText("Chatear").assertCountEquals(0)
    }

    @Test
    fun errorStateRetryCallsBack() {
        var retried = 0
        show(
            ParcheUiState.Error(me.santiagoreina.parcheu.core.error.AppError.Network),
            ParcheCallbacks(onRetry = { retried++ })
        )
        composeRule.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retried)
    }
}
