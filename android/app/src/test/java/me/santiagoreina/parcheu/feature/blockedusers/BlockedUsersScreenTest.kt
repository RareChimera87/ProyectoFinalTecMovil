package me.santiagoreina.parcheu.feature.blockedusers

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.BlockedDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.ui.UiState
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class BlockedUsersScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-11-14T15:00:00Z")
    private val laura =
        BlockedDto("u-laura", "Laura Pérez", "LP", Instant.parse("2026-11-10T15:00:00Z"))

    private fun show(
        state: BlockedUsersState,
        actions: BlockedUsersActions = BlockedUsersActions()
    ) = composeRule.setContent { ParcheUTheme { BlockedUsersScreen(state, actions, now = now) } }

    @Test
    fun listShowsNameDateAndUnblock() {
        val unblocked = mutableListOf<String>()
        show(
            BlockedUsersState(UiState.Loaded(listOf(laura))),
            BlockedUsersActions(onUnblock = { unblocked += it })
        )
        composeRule.onNodeWithText("Laura Pérez").assertIsDisplayed()
        composeRule.onNodeWithText("Bloqueaste hace 4 días").assertIsDisplayed()
        composeRule.onNodeWithTag(BlockedUsersTags.NOTE).assertIsDisplayed()
        composeRule.onNodeWithTag(BlockedUsersTags.unblock("u-laura")).performClick()
        assertEquals(listOf("u-laura"), unblocked)
    }

    @Test
    fun emptyStateExplainsWhereBlocksComeFrom() {
        show(BlockedUsersState(UiState.Empty))
        composeRule.onNodeWithText("No has bloqueado a nadie").assertIsDisplayed()
    }

    @Test
    fun errorOffersRetry() {
        var retries = 0
        show(
            BlockedUsersState(UiState.Error(AppError.Network)),
            BlockedUsersActions(onRetry = { retries++ })
        )
        composeRule.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun whileUnblockingTheButtonsAreDisabled() {
        show(BlockedUsersState(UiState.Loaded(listOf(laura)), unblocking = "u-laura"))
        composeRule.onNodeWithText("Desbloqueando…").assertIsDisplayed()
    }
}
