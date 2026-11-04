package me.santiagoreina.parcheu.designsystem.brand

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi del Wordmark en sus 3 tamaños y ambos temas. Grabar:
 * `./gradlew :designsystem:recordRoborazziDebug`; verificar: `:designsystem:verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h200dp-xhdpi")
class WordmarkScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val options =
        RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))

    private fun capture(size: WordmarkSize, dark: Boolean) {
        val bg = if (dark) Color(0xFF14111F) else Color(0xFFFFFFFF)
        composeRule.setContent {
            Box(Modifier.background(bg).padding(16.dp)) { Wordmark(size = size, darkTheme = dark) }
        }
        val theme = if (dark) "dark" else "light"
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/wordmark_${size.name.lowercase()}_$theme.png",
            roborazziOptions = options
        )
    }

    @Test fun smallDark() = capture(WordmarkSize.Small, true)

    @Test fun smallLight() = capture(WordmarkSize.Small, false)

    @Test fun mediumDark() = capture(WordmarkSize.Medium, true)

    @Test fun mediumLight() = capture(WordmarkSize.Medium, false)

    @Test fun largeDark() = capture(WordmarkSize.Large, true)

    @Test fun largeLight() = capture(WordmarkSize.Large, false)
}
