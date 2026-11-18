package me.santiagoreina.parcheu.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Usage
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.conocer.match.MatchFoundScreen
import me.santiagoreina.parcheu.feature.conocer.match.MatchFoundUiState
import me.santiagoreina.parcheu.feature.conocer.match.MatchQueueScreen
import me.santiagoreina.parcheu.feature.conocer.match.MatchQueueUiState
import me.santiagoreina.parcheu.feature.conocer.match.QueuePhase
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
            state = MatchQueueUiState(
                mode = Mode.AMISTAD,
                dailyQuota = Usage(used = 0, limit = 1, resetsAt = Instant.fromEpochSeconds(0))
            ),
            onModeSelected = {},
            onCancel = {},
            onRetry = {},
            onOpenMatch = {},
            animate = false
        )
    }

    @Composable
    private fun Queue(state: MatchQueueUiState) = MatchQueueScreen(
        state = state,
        onModeSelected = {},
        onCancel = {},
        onRetry = {},
        onOpenMatch = {},
        animate = false
    )

    private val quota = Usage(used = 0, limit = 1, resetsAt = Instant.fromEpochSeconds(0))

    @Test
    fun f1Unlimited() = capture("f1_sin_limite") {
        Queue(
            MatchQueueUiState(
                mode = Mode.PAREJA,
                dailyQuota = quota.copy(used = 3, limit = null)
            )
        )
    }

    @Test
    fun f1AlreadyUsed() = capture("f1_ya_usado") {
        Queue(
            MatchQueueUiState(
                mode = Mode.ESTUDIO,
                phase = QueuePhase.AlreadyUsed("m1"),
                dailyQuota = quota.copy(used = 1)
            )
        )
    }

    @Test
    fun f1Error() = capture("f1_error") {
        Queue(MatchQueueUiState(mode = Mode.PAREJA, phase = QueuePhase.Error(AppError.Underage)))
    }

    @Test
    fun f1Expired() = capture("f1_vencido") {
        Queue(MatchQueueUiState(mode = Mode.AMISTAD, phase = QueuePhase.Expired))
    }

    @Test
    fun f2Expired() = capture("f2_vencido") {
        Found(
            MatchFoundUiState.Loaded(
                testMatch(scenarioAvailable = false, mode = Mode.PAREJA),
                MatchChoice.SIMULATION,
                expired = true
            )
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

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }
}
