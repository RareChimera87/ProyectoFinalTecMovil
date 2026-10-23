package me.santiagoreina.parcheu.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.conocer.match.MatchFoundScreen
import me.santiagoreina.parcheu.feature.conocer.match.MatchFoundUiState
import me.santiagoreina.parcheu.feature.conocer.match.MatchLimitSheetPreview
import me.santiagoreina.parcheu.feature.conocer.match.MatchQueueScreen
import me.santiagoreina.parcheu.feature.conocer.match.MatchQueueUiState
import me.santiagoreina.parcheu.feature.conocer.match.testMatch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de F1, F2 (con y sin escenario, demo) y la hoja de límite en ambos temas (ADR-0022), contra
 * app/src/test/screenshots/match. Grabar con `./gradlew :app:recordRoborazziDevDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class MatchScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { ParcheUTheme(darkTheme = dark) { content() } }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/match/${name}_$theme.png",
            roborazziOptions =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    @Composable
    private fun Found(state: MatchFoundUiState) = MatchFoundScreen(
        state = state,
        onSelect = {},
        onStart = {},
        onRetry = {},
        onSearchAgain = {},
        onClose = {}
    )

    @Test
    fun f1Searching() = capture("f1_buscando") {
        MatchQueueScreen(
            state = MatchQueueUiState(mode = Mode.AMISTAD),
            onModeSelected = {},
            onCancel = {},
            onRetry = {},
            onOpenMatch = {},
            onLimitSeePlus = {},
            onLimitDismiss = {},
            animate = false
        )
    }

    @Test
    fun f2WithScenario() = capture("f2_con_escenario") {
        Found(MatchFoundUiState.Loaded(testMatch(scenarioAvailable = true), MatchChoice.SCENARIO))
    }

    @Test
    fun f2WithoutScenario() = capture("f2_sin_escenario") {
        Found(
            MatchFoundUiState.Loaded(testMatch(scenarioAvailable = false), MatchChoice.SIMULATION)
        )
    }

    @Test
    fun f2Demo() = capture("f2_demo") {
        Found(
            MatchFoundUiState.Loaded(
                testMatch(scenarioAvailable = false, isDemo = true),
                MatchChoice.SIMULATION
            )
        )
    }

    @Test
    fun limitSheet() = capture("hoja_limite_match") {
        MatchLimitSheetPreview()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }
}
