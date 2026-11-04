package me.santiagoreina.parcheu.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.data.simulation.summary
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.simulation.Participants
import me.santiagoreina.parcheu.feature.simulation.ScenarioStep
import me.santiagoreina.parcheu.feature.simulation.SimulationScreen
import me.santiagoreina.parcheu.feature.simulation.SimulationUiState
import me.santiagoreina.parcheu.feature.simulation.StepState
import me.santiagoreina.parcheu.feature.simulations.SimulationsScreen
import me.santiagoreina.parcheu.feature.simulations.SimulationsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de F4 (0 % y 60 %), F5, F6 y la lista 'Ver todas' en ambos temas (ADR-0022), con
 * `changeThreshold` 0,01. Grabar con `./gradlew :app:recordRoborazziDevDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class SimulationScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { ParcheUTheme(darkTheme = dark) { content() } }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/simulation/${name}_$theme.png",
            roborazziOptions =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    private val laura = Participants("Laura", Mode.ESTUDIO, isDemo = false)

    private fun running(percent: Int, current: Int, steps: List<StepState>) =
        SimulationUiState.Running(
            participants = laura,
            steps = steps.mapIndexed { index, state -> ScenarioStep(index + 1, state) },
            currentScenario = current,
            percent = percent
        )

    @Composable
    private fun F4(state: SimulationUiState) = SimulationScreen(
        state,
        onClose = {},
        onReload = {},
        onRetry = {},
        onHome = {},
        animate = false
    )

    @Composable
    private fun F6(state: SimulationsUiState) = SimulationsScreen(
        state,
        onBack = {
        },
        onOpen = {},
        onPickFromParche = {},
        onRandomMatch = {},
        onLoadMore = {},
        onRetry = {}
    )

    @Test
    fun f4Start() = capture("f4_0") {
        F4(running(0, 1, listOf(StepState.IN_PROGRESS, StepState.PENDING, StepState.PENDING)))
    }

    @Test
    fun f4Sixty() = capture("f4_60") {
        F4(running(60, 2, listOf(StepState.DONE, StepState.IN_PROGRESS, StepState.PENDING)))
    }

    @Test
    fun f5Refunded() = capture("f5") {
        F4(SimulationUiState.Failed(pausedAtScenario = 2, refunded = true, canRetry = true))
    }

    @Test
    fun f6Empty() = capture("f6_empty") { F6(SimulationsUiState.Empty(available = 5)) }

    @Test
    fun list() = capture("list") {
        F6(
            SimulationsUiState.Loaded(
                items =
                listOf(
                    summary(id = "a", status = SimulationStatus.DONE, score = 82),
                    summary(id = "b", status = SimulationStatus.RUNNING, mode = Mode.AMISTAD),
                    summary(id = "c", status = SimulationStatus.FAILED, mode = Mode.PAREJA)
                ),
                nextCursor = "next"
            )
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }
}
