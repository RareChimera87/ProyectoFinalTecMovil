package me.santiagoreina.parcheu.feature.twinquestions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import kotlin.time.Instant
import me.santiagoreina.parcheu.R
import me.santiagoreina.parcheu.contract.TwinAnswer
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-night-xhdpi")
class TwinQuestionsContentTest {
    @get:Rule
    val rule = createComposeRule()

    private val answering =
        TwinQuestionsUiState(phase = TwinQuestionsPhase.Answering, questions = sampleQuestions())

    private fun show(
        state: TwinQuestionsUiState,
        answers: MutableList<TwinAnswer>,
        retries: IntArray = IntArray(1)
    ) {
        rule.setContent {
            ParcheUTheme(darkTheme = true) {
                TwinQuestionsContent(
                    state = state,
                    onAnswer = { answers += it },
                    onClose = {},
                    onRetry = { retries[0]++ }
                )
            }
        }
    }

    @Test
    fun swipeLeftAnswersA() {
        val answers = mutableListOf<TwinAnswer>()
        show(answering, answers)
        rule.onNodeWithTag("twinQuestions:card").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(listOf(TwinAnswer.A), answers)
    }

    @Test
    fun swipeRightAnswersB() {
        val answers = mutableListOf<TwinAnswer>()
        show(answering, answers)
        rule.onNodeWithTag("twinQuestions:card").performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertEquals(listOf(TwinAnswer.B), answers)
    }

    @Test
    fun tappingOptionsAndDependsAnswers() {
        val answers = mutableListOf<TwinAnswer>()
        var state by mutableStateOf(answering)
        rule.setContent {
            ParcheUTheme(darkTheme = true) {
                TwinQuestionsContent(state = state, onAnswer = {
                    answers += it
                }, onClose = {}, onRetry = {})
            }
        }
        rule.onNodeWithTag("twinQuestions:optionB").performClick()
        rule.waitForIdle()
        // La respuesta avanza la pregunta: simula el ViewModel entregando la siguiente.
        state = state.copy(answers = mapOf(1 to TwinAnswer.B))
        rule.waitForIdle()
        rule.onNodeWithTag("twinQuestions:depends").performClick()
        rule.waitForIdle()
        assertEquals(listOf(TwinAnswer.B, TwinAnswer.DEPENDS), answers)
    }

    @Test
    fun showsProgressNofTotal() {
        show(answering.copy(answers = (1..3).associateWith { TwinAnswer.A }), mutableListOf())
        rule.onNodeWithTag("twinQuestions:progressText").assertIsDisplayed()
        rule.onNodeWithTag("twinQuestions:questionText").assertIsDisplayed()
    }

    @Test
    fun errorStateOffersRetry() {
        val retries = IntArray(1)
        show(answering.copy(phase = TwinQuestionsPhase.Error), mutableListOf(), retries)
        rule.onNodeWithTag("twinQuestions:retry").performClick()
        assertEquals(1, retries[0])
    }

    private fun errorMessage(error: AppError): String {
        show(answering.copy(phase = TwinQuestionsPhase.Error, error = error), mutableListOf())
        return rule.onNodeWithTag("twinQuestions:errorMessage").fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString { it.text }
    }

    @Test
    fun quotaWithoutKindTellsToComeBackTomorrowWithTheResetDate() {
        val text = errorMessage(AppError.QuotaExceeded(null, Instant.parse("2026-10-30T05:00:00Z")))
        assertTrue(text, "vuelve mañana" in text && "viernes 30 de octubre" in text)
    }

    @Test
    fun quotaWithoutKindNorResetStillSaysTomorrow() {
        assertTrue("vuelve mañana" in errorMessage(AppError.QuotaExceeded(null, null)))
    }

    @Test
    fun rateLimitedAsksToWaitAMoment() {
        assertTrue("Espera un momento" in errorMessage(AppError.RateLimited))
    }

    private fun showWithClose(showClose: Boolean, closes: IntArray) {
        rule.setContent {
            ParcheUTheme(darkTheme = true) {
                TwinQuestionsContent(
                    state = answering,
                    onAnswer = {},
                    onClose = { closes[0]++ },
                    onRetry = {},
                    showClose = showClose
                )
            }
        }
    }

    private val closeLabel: String
        get() = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.twin_questions_close)

    @Test
    fun closeButtonInvokesOnCloseWhenVisible() {
        val closes = IntArray(1)
        showWithClose(showClose = true, closes = closes)
        rule.onNodeWithContentDescription(closeLabel).performClick()
        assertEquals(1, closes[0])
    }

    @Test
    fun closeButtonIsHiddenWhenThereIsNothingToGoBackTo() {
        showWithClose(showClose = false, closes = IntArray(1))
        rule.onNodeWithContentDescription(closeLabel).assertDoesNotExist()
        rule.onNodeWithTag("twinQuestions:progressText").assertIsDisplayed()
    }
}
