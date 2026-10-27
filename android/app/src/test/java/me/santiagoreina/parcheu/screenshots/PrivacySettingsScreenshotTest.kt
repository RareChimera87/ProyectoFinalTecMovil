package me.santiagoreina.parcheu.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.privacysettings.PrivacySettingsActions
import me.santiagoreina.parcheu.feature.privacysettings.PrivacySettingsScreen
import me.santiagoreina.parcheu.feature.privacysettings.PrivacySettingsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de H3 'Ajustes de privacidad' (E8.2) en ambos temas, con la pantalla alta para que entre completa,
 * incluida la fila 'Cerrar sesión'. Regresión contra app/src/test/screenshots/privacysettings; grabar con
 * `./gradlew :app:testDevDebugUnitTest -Proborazzi.test.record=true`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h1400dp-xhdpi")
class PrivacySettingsScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private val defaults = PrivacySettingsUiState(
        loading = false,
        visibility = Visibility.ANONYMOUS,
        modes = mapOf(Mode.AMISTAD to true, Mode.PAREJA to false, Mode.ESTUDIO to true),
        learning = mapOf(Mode.AMISTAD to true, Mode.PAREJA to false, Mode.ESTUDIO to true)
    )

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { ParcheUTheme(darkTheme = dark) { content() } }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/privacysettings/${name}_$theme.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    @Test
    fun defaultSettings() = capture("privacidad") {
        PrivacySettingsScreen(defaults, PrivacySettingsActions(), animateLoading = false)
    }

    @Test
    fun allModesOnVisibleByName() = capture("privacidad_todo_activo") {
        PrivacySettingsScreen(
            defaults.copy(
                visibility = Visibility.NAME,
                modes = Mode.entries.associateWith { true },
                learning = Mode.entries.associateWith { true }
            ),
            PrivacySettingsActions(),
            animateLoading = false
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
