package me.santiagoreina.parcheu.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.notifications.NotificationsCallbacks
import me.santiagoreina.parcheu.feature.notifications.NotificationsSamples
import me.santiagoreina.parcheu.feature.notifications.NotificationsScreen
import me.santiagoreina.parcheu.feature.notifications.NotificationsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de C2 (E5.4): con no leídas (incluye el aviso bloqueado 'quieren conectar contigo'), todo leído
 * y vacío, en oscuro. Se graban con `-Proborazzi.test.record=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class NotificationsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, state: NotificationsUiState) {
        composeRule.setContent {
            ParcheUTheme(darkTheme = true) {
                NotificationsScreen(state, NotificationsCallbacks(), now = NotificationsSamples.now)
            }
        }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/notifications/c2_$name.png",
            roborazziOptions =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    /** Con no leídas y el bloqueado de 'Esta semana'. */
    @Test
    fun withUnread() = capture("no_leidas", NotificationsSamples.withUnread)

    @Test
    fun allRead() = capture("leidas", NotificationsSamples.allRead)

    @Test
    fun empty() = capture("vacio", NotificationsSamples.empty)
}
