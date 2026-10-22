package me.santiagoreina.parcheu.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.data.results.fullReport
import me.santiagoreina.parcheu.data.results.lockedReport
import me.santiagoreina.parcheu.data.results.sampleTranscript
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.results.DecisionState
import me.santiagoreina.parcheu.feature.results.ResultsActions
import me.santiagoreina.parcheu.feature.results.ResultsContent
import me.santiagoreina.parcheu.feature.results.ResultsScreen
import me.santiagoreina.parcheu.feature.results.ResultsUiState
import me.santiagoreina.parcheu.feature.results.TranscriptState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de F7 (informe del mes disponible y ya consumido), F8 (oscuro) / I3 (la misma pantalla en
 * claro), 'esperando a la otra persona', perfil demo y la conversación de los gemelos, en ambos temas
 * (ADR-0022) con `changeThreshold` 0,01. Grabar con `./gradlew :app:recordRoborazziDevDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class ResultsScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private fun capture(name: String, content: ResultsContent) {
        composeRule.setContent {
            ParcheUTheme(darkTheme = dark) { ResultsScreen(ResultsUiState.Loaded(content), ResultsActions()) }
        }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/results/${name}_$theme.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            ),
        )
    }

    private fun content(
        full: Boolean = false,
        unlockAvailable: Boolean = true,
        decision: DecisionState = DecisionState.PENDING,
        isDemo: Boolean = false,
    ) = ResultsContent(
        otherLabel = "Alguien de la UIS",
        mode = Mode.ESTUDIO,
        isDemo = isDemo,
        plus = full,
        report = if (full) fullReport() else lockedReport(unlockAvailable),
        decision = decision,
        usedMonth = "octubre",
        resetMonth = "noviembre",
    )

    @Test
    fun f7Available() = capture("f7_available", content())

    @Test
    fun f7Consumed() = capture("f7_consumed", content(unlockAvailable = false))

    /** F8 en oscuro; la captura en claro es I3. */
    @Test
    fun f8() = capture(if (dark) "f8" else "i3", content(full = true))

    @Test
    fun waiting() = capture("waiting", content(unlockAvailable = false, decision = DecisionState.WAITING))

    @Test
    fun demo() = capture("f7_demo", content(isDemo = true))

    @Test
    fun transcript() = capture(
        "transcript",
        content(full = true).copy(transcript = TranscriptState.Loaded(sampleTranscript())),
    )

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }
}
