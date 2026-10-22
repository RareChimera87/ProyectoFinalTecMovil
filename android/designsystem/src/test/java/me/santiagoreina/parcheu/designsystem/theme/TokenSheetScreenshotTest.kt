package me.santiagoreina.parcheu.designsystem.theme

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captura Roborazzi de la hoja de tokens (equivalente a S0) en ambos temas. Regresión contra la captura
 * versionada en designsystem/src/test/screenshots con `changeThreshold` 0,01 (ADR-0022).
 * Grabar: `./gradlew :designsystem:recordRoborazziDebug`; verificar: `:designsystem:verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h1100dp-xhdpi")
class TokenSheetScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tokenSheetDark() = capture(darkTheme = true, name = "token_sheet_dark")

    @Test
    fun tokenSheetLight() = capture(darkTheme = false, name = "token_sheet_light")

    private fun capture(darkTheme: Boolean, name: String) {
        composeRule.setContent { ParcheUTheme(darkTheme = darkTheme) { TokenSheet() } }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/$name.png",
            roborazziOptions = ROBORAZZI_OPTIONS,
        )
    }

    companion object {
        private val ROBORAZZI_OPTIONS =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            )
    }
}
