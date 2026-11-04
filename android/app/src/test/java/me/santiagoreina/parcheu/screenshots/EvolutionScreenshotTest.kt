package me.santiagoreina.parcheu.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.evolution.EvolutionCallbacks
import me.santiagoreina.parcheu.feature.evolution.EvolutionSamples
import me.santiagoreina.parcheu.feature.evolution.EvolutionScreen
import me.santiagoreina.parcheu.feature.evolution.EvolutionUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de D9 en sus tres estados (con historial, menos de 2 meses y bloqueado) en ambos temas
 * (ADR-0022, E6.5). Se graban con `-Proborazzi.test.record=true`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class EvolutionScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private fun capture(name: String, state: EvolutionUiState) {
        composeRule.setContent {
            ParcheUTheme(darkTheme = dark) {
                EvolutionScreen(state, EvolutionCallbacks(), animate = false)
            }
        }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/parche/${name}_$theme.png",
            roborazziOptions =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    @Test
    fun d9WithHistory() = capture(
        "d9_evolucion",
        EvolutionUiState.Content(EvolutionSamples.full, selected = "2026-09")
    )

    @Test
    fun d9TooEarly() =
        capture("d9_menos_de_2_meses", EvolutionUiState.TooEarly(EvolutionSamples.tooEarly))

    @Test
    fun d9Locked() = capture("d9_bloqueado", EvolutionUiState.Locked)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }
}
