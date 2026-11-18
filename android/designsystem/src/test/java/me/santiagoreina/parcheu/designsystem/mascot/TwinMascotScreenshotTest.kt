package me.santiagoreina.parcheu.designsystem.mascot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
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
 * Capturas Roborazzi de "La P que mira": 4 tamaños (S ya simplificada), 4 colores, error, pose de
 * "conversando" y la fila del cupo a 24 dp sobre el héroe violeta, en ambos temas (ADR-0022).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h560dp-xhdpi")
class TwinMascotScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mascotDark() = capture(true, "twin_mascot_dark")

    @Test
    fun mascotLight() = capture(false, "twin_mascot_light")

    private fun capture(dark: Boolean, name: String) {
        composeRule.setContent { ParcheUTheme(darkTheme = dark) { Sheet() } }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/$name.png",
            roborazziOptions =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    @Composable
    private fun Sheet() {
        Column(
            Modifier.background(ParcheUTheme.colors.bg).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                TwinMascotSize.entries.forEach { TwinMascot(size = it, animationsEnabled = false) }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                TwinMascotTone.entries.forEach {
                    TwinMascot(size = TwinMascotSize.M, tone = it, animationsEnabled = false)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                TwinMascot(size = TwinMascotSize.L, error = true, animationsEnabled = false)
                TwinMascot(
                    size = TwinMascotSize.XL,
                    tone = TwinMascotTone.Couple,
                    error = true,
                    animationsEnabled = false
                )
                TwinMascot(size = TwinMascotSize.S, error = true, animationsEnabled = false)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                TwinMascot(size = TwinMascotSize.L, talking = true, animationsEnabled = false)
                TwinMascotGlyph(
                    width = TwinMascotSize.L.width,
                    color = ParcheUTheme.colors.study.solid,
                    mouth = MascotMouth.Talking,
                    mirrored = true
                )
                TwinMascot(size = TwinMascotSize.S, talking = true, animationsEnabled = false)
            }
            Row(
                Modifier.background(ParcheUTheme.colors.hero).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val c = ParcheUTheme.colors
                repeat(5) { i ->
                    TwinMascotGlyph(
                        width = 24.dp,
                        color = if (i < 2) c.onHero else c.heroQuiet,
                        eyeColor = c.hero
                    )
                }
            }
        }
    }
}
