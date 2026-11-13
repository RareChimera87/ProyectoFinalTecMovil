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
 * Capturas de los componentes de la dirección A "Parche Vivo" (HeroBlock, QuotaMascots, ModeSpineCard,
 * AccessTile, chips, duotono, logo e ícono) en ambos temas. Referencias en
 * src/test/screenshots/parchevivo (ADR-0022, `changeThreshold` 0,01).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h1400dp-xhdpi")
class ParcheVivoScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun heroesDark() = capture("heroes", true) { ParcheVivoGallery.Heroes() }

    @Test fun heroesLight() = capture("heroes", false) { ParcheVivoGallery.Heroes() }

    @Test fun spineCardsDark() = capture("spine_cards", true) { ParcheVivoGallery.SpineCards() }

    @Test fun spineCardsLight() = capture("spine_cards", false) { ParcheVivoGallery.SpineCards() }

    @Test fun duotoneDark() = capture("duotone", true) { ParcheVivoGallery.Duotones() }

    @Test fun duotoneLight() = capture("duotone", false) { ParcheVivoGallery.Duotones() }

    @Test fun brandDark() = capture("brand", true) { ParcheVivoGallery.Brand() }

    @Test fun brandLight() = capture("brand", false) { ParcheVivoGallery.Brand() }

    private fun capture(name: String, darkTheme: Boolean, content: @Composable () -> Unit) {
        composeRule.setContent { ParcheUTheme(darkTheme = darkTheme) { content() } }
        val suffix = if (darkTheme) "dark" else "light"
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/parchevivo/${name}_$suffix.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }
}
