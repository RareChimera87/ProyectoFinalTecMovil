package me.santiagoreina.parcheu.feature.notifications

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.Notifications
import me.santiagoreina.parcheu.navigation.Paywall
import me.santiagoreina.parcheu.navigation.Results
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Criterio de E5.4 (UI con Fakes): tocar una notificación navega a su ruta (la misma tabla de E5.3,
 * `DeepLinks`) y la marca como leída, en pantalla y en el backend.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class NotificationsRouteTest {
    @get:Rule(order = 0)
    val mainRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private class RecordingNavigator : AppNavigator {
        val routes = mutableListOf<AppRoute>()
        override fun navigate(route: AppRoute) {
            routes += route
        }
        override fun navigateToTopLevel(route: AppRoute) = Unit
        override fun replaceAll(route: AppRoute) = Unit
        override fun back(): Boolean = true
        override fun canGoBack(): Boolean = true
    }

    private val unreadDescription =
        SemanticsMatcher("anunciada como no leída") { node ->
            node.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }
                ?.any { it.startsWith("No leída") } == true
        }

    private fun show(repo: FakeNotificationsRepository): RecordingNavigator {
        val navigator = RecordingNavigator()
        val viewModel = NotificationsViewModel(repo)
        composeRule.setContent {
            ParcheUTheme(darkTheme = true) {
                NotificationsRoute(Notifications, navigator, viewModel = viewModel)
            }
        }
        return navigator
    }

    @Test
    fun tappingANotificationOpensItsRouteAndMarksItRead() {
        val repo =
            FakeNotificationsRepository(ApiResult.Success(NotificationsSamples.withUnread.inbox))
        val navigator = show(repo)
        val row = composeRule.onNodeWithTag(NotificationsTags.item("n-result"))
        row.assertIsDisplayed().assert(unreadDescription)

        row.performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<AppRoute>(Results("sim-daniela")), navigator.routes)
        assertEquals(listOf(listOf("n-result")), repo.markReadCalls)
        composeRule.onNodeWithTag(NotificationsTags.item("n-result")).assert(!unreadDescription)
    }

    @Test
    fun tappingTheLockedItemOpensThePaywall() {
        val repo =
            FakeNotificationsRepository(ApiResult.Success(NotificationsSamples.withUnread.inbox))
        val navigator = show(repo)
        composeRule.onNodeWithTag(
            NotificationsTags.item("n-locked")
        ).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(listOf<AppRoute>(Paywall(PAYWALL_SOURCE_NOTIFICATIONS)), navigator.routes)
    }

    @Test
    fun markAllReadClearsEveryUnreadDot() {
        val repo =
            FakeNotificationsRepository(ApiResult.Success(NotificationsSamples.withUnread.inbox))
        show(repo)
        composeRule.onNodeWithTag(NotificationsTags.MARK_ALL).performClick()
        composeRule.waitForIdle()
        assertEquals(1, repo.markReadCalls.size)
        assertEquals(
            setOf("n-result", "n-intro", "n-locked"),
            repo.markReadCalls.single()!!.toSet()
        )
        listOf("n-result", "n-intro", "n-locked").forEach { id ->
            composeRule.onNodeWithTag(
                NotificationsTags.item(id)
            ).performScrollTo().assert(!unreadDescription)
        }
    }
}
