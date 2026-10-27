package me.santiagoreina.parcheu.feature.notifications.requests

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import me.santiagoreina.parcheu.contract.ConnectRequestsDto
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.feature.notifications.FakeNotificationsRepository
import me.santiagoreina.parcheu.feature.notifications.NotificationsRoute
import me.santiagoreina.parcheu.feature.notifications.NotificationsSamples
import me.santiagoreina.parcheu.feature.notifications.NotificationsViewModel
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

/** La sección de E6.6 dentro de la ruta de C2: carga al entrar y navega con su ViewModel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class NotificationsRequestsRouteTest {
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

    private fun show(
        dto: ConnectRequestsDto
    ): Pair<RecordingNavigator, FakeConnectRequestsRepository> {
        val navigator = RecordingNavigator()
        val requests = FakeConnectRequestsRepository(ApiResult.Success(dto))
        val notifications =
            NotificationsViewModel(
                FakeNotificationsRepository(
                    ApiResult.Success(NotificationsSamples.withUnread.inbox)
                )
            )
        composeRule.setContent {
            ParcheUTheme(darkTheme = true) {
                NotificationsRoute(
                    Notifications,
                    navigator,
                    viewModel = notifications,
                    requestsViewModel = ConnectRequestsViewModel(requests)
                )
            }
        }
        return navigator to requests
    }

    @Test
    fun freeLockedItemOpensThePaywall() {
        val (navigator, requests) = show(ConnectRequestsSamples.free)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ConnectRequestsTags.LOCKED).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf<AppRoute>(Paywall(PAYWALL_SOURCE_CONNECT_REQUESTS)), navigator.routes)
        assertEquals(true, requests.calls >= 1)
    }

    @Test
    fun plusItemOpensItsResult() {
        val (navigator, _) = show(ConnectRequestsSamples.plus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ConnectRequestsTags.item("sim-2")).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf<AppRoute>(Results("sim-2")), navigator.routes)
    }
}
