package me.santiagoreina.parcheu.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.consent.ConsentScreen
import me.santiagoreina.parcheu.feature.consent.ConsentUiState
import me.santiagoreina.parcheu.feature.modes.ModesScreen
import me.santiagoreina.parcheu.feature.modes.ModesUiState
import me.santiagoreina.parcheu.feature.modes.ParejaDialogState
import me.santiagoreina.parcheu.feature.twinready.TwinReadyScreen
import me.santiagoreina.parcheu.feature.twinready.TwinReadyUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de B2-B5 en ambos temas (E2.5, ADR-0022): B2 vacío y con modos, B3 con la fecha verificada en
 * solo lectura, B4 y B5. Regresión contra app/src/test/screenshots/modes; grabar con
 * `./gradlew :app:recordRoborazziDevDebug`. La comparación con los mockups la hace I2 a mano.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class ModesScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { ParcheUTheme(darkTheme = dark) { content() } }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/modes/${name}_$theme.png",
            roborazziOptions =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            ),
        )
    }

    @Composable
    private fun Modes(state: ModesUiState) {
        ModesScreen(
            state = state,
            showBack = true,
            onBack = {},
            onToggle = { _, _ -> },
            onContinue = {},
            onRetry = {},
            onDialogConfirmedChange = {},
            onDialogConfirm = {},
            onDialogCancel = {},
            animateLoading = false,
        )
    }

    private val loaded = ModesUiState(loading = false)

    @Test
    fun b2Empty() = capture("b2_empty") { Modes(loaded) }

    @Test
    fun b2Selected() = capture("b2_selected") {
        Modes(loaded.copy(modes = mapOf(Mode.AMISTAD to true, Mode.PAREJA to true, Mode.ESTUDIO to false)))
    }

    @Test
    fun b3() = capture("b3") {
        Modes(
            loaded.copy(
                modes = mapOf(Mode.AMISTAD to true, Mode.PAREJA to false, Mode.ESTUDIO to false),
                dialog = ParejaDialogState(birthDate = "14 / 03 / 2005", age = 21, confirmed = true),
            ),
        )
    }

    @Test
    fun b4() = capture("b4") {
        ConsentScreen(ConsentUiState(), showBack = true, onBack = {
        }, onAccept = {}, onNotNow = {}, animateMascot = false)
    }

    @Test
    fun b5() = capture("b5") {
        TwinReadyScreen(
            TwinReadyUiState(listOf(Mode.AMISTAD, Mode.PAREJA, Mode.ESTUDIO)),
            onViewParche = {},
            onGoHome = {},
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
