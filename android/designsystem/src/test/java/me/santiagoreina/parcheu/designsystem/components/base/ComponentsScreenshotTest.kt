package me.santiagoreina.parcheu.designsystem.components.base

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
 * Capturas Roborazzi de cada familia de componentes base con sus estados (habilitado, deshabilitado y
 * seleccionado) en ambos temas. Regresión contra src/test/screenshots/components con `changeThreshold`
 * 0,01 (ADR-0022). Grabar: `./gradlew :designsystem:recordRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h2400dp-xhdpi")
class ComponentsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun buttonsDark() = capture("buttons", true) { Galleries.Buttons() }

    @Test fun buttonsLight() = capture("buttons", false) { Galleries.Buttons() }

    @Test fun chipsDark() = capture("chips", true) { Galleries.Chips() }

    @Test fun chipsLight() = capture("chips", false) { Galleries.Chips() }

    @Test fun segmentedDark() = capture("segmented", true) { Galleries.Segmented() }

    @Test fun segmentedLight() = capture("segmented", false) { Galleries.Segmented() }

    @Test fun selectionDark() = capture("selection", true) { Galleries.Selection() }

    @Test fun selectionLight() = capture("selection", false) { Galleries.Selection() }

    @Test fun fieldsDark() = capture("fields", true) { Galleries.Fields() }

    @Test fun fieldsLight() = capture("fields", false) { Galleries.Fields() }

    @Test fun cardsDark() = capture("cards", true) { Galleries.Cards() }

    @Test fun cardsLight() = capture("cards", false) { Galleries.Cards() }

    @Test fun progressDark() = capture("progress", true) { Galleries.Progress() }

    @Test fun progressLight() = capture("progress", false) { Galleries.Progress() }

    @Test fun barsDark() = capture("bars", true) { Galleries.Bars() }

    @Test fun barsLight() = capture("bars", false) { Galleries.Bars() }

    @Test fun navBarsDark() = capture("navbar", true) { Galleries.NavBars() }

    @Test fun navBarsLight() = capture("navbar", false) { Galleries.NavBars() }

    private fun capture(name: String, darkTheme: Boolean, content: @Composable () -> Unit) {
        composeRule.setContent { ParcheUTheme(darkTheme = darkTheme) { content() } }
        val suffix = if (darkTheme) "dark" else "light"
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/components/${name}_$suffix.png",
            roborazziOptions =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }
}
