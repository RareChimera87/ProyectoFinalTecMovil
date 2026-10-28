package me.santiagoreina.parcheu.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.notifications.NotificationsCallbacks
import me.santiagoreina.parcheu.feature.notifications.NotificationsSamples
import me.santiagoreina.parcheu.feature.notifications.NotificationsScreen
import me.santiagoreina.parcheu.feature.notifications.requests.ConnectRequestsCallbacks
import me.santiagoreina.parcheu.feature.notifications.requests.ConnectRequestsSamples
import me.santiagoreina.parcheu.feature.notifications.requests.ConnectRequestsSection
import me.santiagoreina.parcheu.feature.notifications.requests.ConnectRequestsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de la sección 'Quieren conectar contigo' de C2 (E6.6) dentro de la bandeja, para gratis (ítem
 * bloqueado con conteo) y ParcheU+ (lista enmascarada con afinidad). Se graban con
 * `-Proborazzi.test.record=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class ConnectRequestsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, section: ConnectRequestsUiState) {
        composeRule.setContent {
            ParcheUTheme(darkTheme = true) {
                NotificationsScreen(
                    NotificationsSamples.withUnread,
                    NotificationsCallbacks(),
                    now = NotificationsSamples.now,
                    requestsSection = {
                        ConnectRequestsSection(section, ConnectRequestsCallbacks())
                    }
                )
            }
        }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/notifications/c2_solicitudes_$name.png",
            roborazziOptions =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    @Test
    fun free() = capture("gratis", ConnectRequestsUiState.Locked(ConnectRequestsSamples.free.count))

    @Test
    fun plus() = capture("plus", ConnectRequestsUiState.Plus(ConnectRequestsSamples.plus.items!!))
}
