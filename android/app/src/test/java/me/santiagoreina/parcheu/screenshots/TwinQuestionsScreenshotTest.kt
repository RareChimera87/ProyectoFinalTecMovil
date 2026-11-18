package me.santiagoreina.parcheu.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.contract.TwinAnswer
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.twinquestions.TwinQuestionsContent
import me.santiagoreina.parcheu.feature.twinquestions.TwinQuestionsPhase
import me.santiagoreina.parcheu.feature.twinquestions.TwinQuestionsUiState
import me.santiagoreina.parcheu.feature.twinquestions.seedQuestions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de B1 (ADR-0022) en ambos temas: las preguntas 1 (fogata nocturna), 2 (lámpara en penumbra),
 * 4, 9 (el celular de q09_a, encuadrado para que no se lea la marca de su pantalla) y 12 con sus fotos en
 * duotono, y el estado de error.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class TwinQuestionsScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val rule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private fun state(
        answered: Int,
        phase: TwinQuestionsPhase = TwinQuestionsPhase.Answering,
        error: AppError? = null
    ) = TwinQuestionsUiState(
        phase = phase,
        // Las preguntas reales de la semilla: cada una con sus dos fotos.
        questions = seedQuestions,
        answers = (1..answered).associateWith { TwinAnswer.A },
        error = error
    )

    private fun capture(name: String, state: TwinQuestionsUiState) {
        rule.setContent {
            ParcheUTheme(darkTheme = dark) {
                TwinQuestionsContent(state = state, onAnswer = {}, onClose = {}, onRetry = {})
            }
        }
        rule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/twinquestions/b1_${name}_$theme.png",
            // Umbral documentado en arquitectura.md §2.7 y ADR-0022 (igual que OnboardingScreenshotTest).
            roborazziOptions =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    @Test fun question1() = capture("q01", state(0))

    @Test fun question2() = capture("q02", state(1))

    @Test fun question4() = capture("q04", state(3))

    @Test fun question9() = capture("q09", state(8))

    @Test fun question12() = capture("q12", state(11))

    @Test fun error() = capture("error", state(12, TwinQuestionsPhase.Error, AppError.Network))

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }
}
