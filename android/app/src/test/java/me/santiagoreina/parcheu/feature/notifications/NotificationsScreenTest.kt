package me.santiagoreina.parcheu.feature.notifications

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Estados de C2 (E5.4). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class NotificationsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        state: NotificationsUiState,
        callbacks: NotificationsCallbacks = NotificationsCallbacks()
    ) = composeRule.setContent {
        ParcheUTheme(darkTheme = true) {
            NotificationsScreen(state, callbacks, now = NotificationsSamples.now)
        }
    }

    @Test
    fun groupsTodayAndThisWeek() {
        show(NotificationsSamples.withUnread)
        composeRule.onNodeWithText("HOY").assertIsDisplayed()
        composeRule.onNodeWithText("ESTA SEMANA").assertIsDisplayed()
        composeRule.onNodeWithText("Resultados · hace 1 h").assertIsDisplayed()
        composeRule.onNodeWithTag(NotificationsTags.MARK_ALL).assertIsEnabled()
    }

    @Test
    fun lockedItemShowsTheLockAndTheUpsell() {
        show(NotificationsSamples.withUnread)
        composeRule.onNodeWithTag(NotificationsTags.item("n-locked")).performScrollTo()
        composeRule.onNodeWithTag(
            NotificationsTags.locked("n-locked"),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            "Míralas con ParcheU+ · sáb",
            useUnmergedTree = true
        ).assertIsDisplayed()
    }

    @Test
    fun markAllReadIsDisabledWithNothingUnread() {
        show(NotificationsSamples.allRead)
        composeRule.onNodeWithTag(NotificationsTags.MARK_ALL).assertIsNotEnabled()
    }

    @Test
    fun emptyStateHasNoMarkAll() {
        show(NotificationsSamples.empty)
        composeRule.onNodeWithTag(NotificationsTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("Todo al día").assertIsDisplayed()
        composeRule.onAllNodesWithTag(NotificationsTags.MARK_ALL).assertCountEquals(0)
    }

    @Test
    fun errorOffersRetryAndBackWorks() {
        var retries = 0
        var backs = 0
        show(
            NotificationsUiState.Error(AppError.Network),
            NotificationsCallbacks(onRetry = { retries++ }, onBack = { backs++ })
        )
        composeRule.onNodeWithText("Reintentar").performClick()
        composeRule.onNodeWithContentDescription("Volver").performClick()
        assertEquals(1, retries)
        assertEquals(1, backs)
    }

    @Test
    fun actionErrorIsShownAndDismissable() {
        var dismissed = 0
        show(
            NotificationsSamples.withUnread.copy(actionError = AppError.Network),
            NotificationsCallbacks(onDismissError = { dismissed++ })
        )
        composeRule.onNodeWithTag(NotificationsTags.ACTION_ERROR).performClick()
        assertEquals(1, dismissed)
    }
}
