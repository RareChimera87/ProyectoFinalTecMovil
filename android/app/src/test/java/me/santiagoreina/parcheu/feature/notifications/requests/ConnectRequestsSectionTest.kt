package me.santiagoreina.parcheu.feature.notifications.requests

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.santiagoreina.parcheu.contract.ConnectRequest
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Estados de la sección 'Quieren conectar contigo' de C2 (E6.6). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class ConnectRequestsSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        state: ConnectRequestsUiState,
        callbacks: ConnectRequestsCallbacks = ConnectRequestsCallbacks()
    ) = composeRule.setContent {
        ParcheUTheme(darkTheme = true) { ConnectRequestsSection(state, callbacks) }
    }

    @Test
    fun hiddenDrawsNothing() {
        show(ConnectRequestsUiState.Hidden)
        composeRule.onAllNodesWithTag(ConnectRequestsTags.SECTION).assertCountEquals(0)
    }

    @Test
    fun freeShowsTheLockedItemWithTheCountAndNoPeople() {
        var opened = 0
        show(
            ConnectRequestsUiState.Locked(2),
            ConnectRequestsCallbacks(onOpenLocked = {
                opened++
            })
        )
        composeRule.onNodeWithText("QUIEREN CONECTAR CONTIGO").assertIsDisplayed()
        composeRule.onNodeWithTag(ConnectRequestsTags.LOCKED).assertIsDisplayed()
        composeRule.onNodeWithText("2 personas quieren conectar contigo", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag(ConnectRequestsTags.item("sim-1")).assertCountEquals(0)
        composeRule.onNodeWithTag(ConnectRequestsTags.LOCKED).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun singularCount() {
        show(ConnectRequestsUiState.Locked(1))
        composeRule.onNodeWithText("1 persona quiere conectar contigo", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun plusListsEveryoneMaskedWithTheirAffinity() {
        val opened = mutableListOf<ConnectRequest>()
        show(
            ConnectRequestsUiState.Plus(
                listOf(ConnectRequestsSamples.first, ConnectRequestsSamples.second)
            ),
            ConnectRequestsCallbacks(onOpen = { opened += it })
        )
        composeRule.onAllNodesWithTag(ConnectRequestsTags.LOCKED).assertCountEquals(0)
        composeRule.onNodeWithTag(ConnectRequestsTags.item("sim-1")).assertIsDisplayed()
        composeRule.onNodeWithText("84 % afín", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(ConnectRequestsTags.item("sim-2")).performClick()
        assertEquals(listOf(ConnectRequestsSamples.second), opened)
    }
}
