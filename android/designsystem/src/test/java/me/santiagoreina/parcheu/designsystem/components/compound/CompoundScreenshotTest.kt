package me.santiagoreina.parcheu.designsystem.components.compound

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas Roborazzi de cada componente compuesto en ambos temas (criterio de E1.4). Referencias en
 * src/test/screenshots/compound con `changeThreshold` 0,01 (ADR-0022). Grabar:
 * `./gradlew :designsystem:recordRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h2400dp-xhdpi")
class CompoundScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun sheetsDark() = capture("sheets", true) { CompoundGalleries.Sheets() }

    @Test fun sheetsLight() = capture("sheets", false) { CompoundGalleries.Sheets() }

    @Test fun avatarsDark() = capture("avatars", true) { CompoundGalleries.Avatars() }

    @Test fun avatarsLight() = capture("avatars", false) { CompoundGalleries.Avatars() }

    @Test fun chatDark() = capture("chat", true) { CompoundGalleries.Chat() }

    @Test fun chatLight() = capture("chat", false) { CompoundGalleries.Chat() }

    @Test fun bannersDark() = capture("banners", true) { CompoundGalleries.Banners() }

    @Test fun bannersLight() = capture("banners", false) { CompoundGalleries.Banners() }

    @Test fun statesDark() = capture("states", true) { CompoundGalleries.States() }

    @Test fun statesLight() = capture("states", false) { CompoundGalleries.States() }

    @Test fun rowsDark() = capture("rows", true) { CompoundGalleries.Rows() }

    @Test fun rowsLight() = capture("rows", false) { CompoundGalleries.Rows() }

    @Test fun footersDark() = capture("footers", true) { CompoundGalleries.Footers() }

    @Test fun footersLight() = capture("footers", false) { CompoundGalleries.Footers() }

    @Test fun celebrationDark() = capture("celebration", true) { CompoundGalleries.Celebration() }

    @Test fun celebrationLight() = capture("celebration", false) { CompoundGalleries.Celebration() }

    @Test fun chartsDark() = capture("charts", true) { CompoundGalleries.Charts() }

    @Test fun chartsLight() = capture("charts", false) { CompoundGalleries.Charts() }

    private fun capture(name: String, darkTheme: Boolean, content: @Composable () -> Unit) {
        composeRule.setContent { ParcheUTheme(darkTheme = darkTheme) { content() } }
        val suffix = if (darkTheme) "dark" else "light"
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/compound/${name}_$suffix.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }
}
