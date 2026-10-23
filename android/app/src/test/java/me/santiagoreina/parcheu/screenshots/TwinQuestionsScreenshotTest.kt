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
import me.santiagoreina.parcheu.feature.twinquestions.sampleQuestions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Capturas de B1 (ADR-0022) en las preguntas 1, 4 y 12 y en el estado de error. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-night-xhdpi")
class TwinQuestionsScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun state(
        answered: Int,
        phase: TwinQuestionsPhase = TwinQuestionsPhase.Answering,
        error: AppError? = null
    ) = TwinQuestionsUiState(
        phase = phase,
        questions = sampleQuestions().map {
            it.copy(
                text = "Un viernes por la noche, ¿qué prefieres? (${it.id})",
                optionA = "Salir con todo el parche",
                optionB = "Serie y comida en casa"
            )
        },
        answers = (1..answered).associateWith { TwinAnswer.A },
        error = error
    )

    private fun capture(name: String, state: TwinQuestionsUiState) {
        rule.setContent {
            ParcheUTheme(darkTheme = true) {
                TwinQuestionsContent(state = state, onAnswer = {}, onClose = {}, onRetry = {})
            }
        }
        rule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/twin_questions_$name.png",
            // Umbral documentado en arquitectura.md §2.7 y ADR-0022 (igual que OnboardingScreenshotTest).
            roborazziOptions =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    @Test fun question1() = capture("q01", state(0))

    @Test fun question4() = capture("q04", state(3))

    @Test fun question12() = capture("q12", state(11))

    @Test fun error() = capture("error", state(12, TwinQuestionsPhase.Error, AppError.Network))
}
