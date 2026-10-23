package me.santiagoreina.parcheu.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.contract.IntroductionStatus
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.presentar.IntroProposalActions
import me.santiagoreina.parcheu.feature.presentar.IntroProposalContent
import me.santiagoreina.parcheu.feature.presentar.IntroProposalScreen
import me.santiagoreina.parcheu.feature.presentar.IntroProposalUiState
import me.santiagoreina.parcheu.feature.presentar.IntroResultActions
import me.santiagoreina.parcheu.feature.presentar.IntroResultContent
import me.santiagoreina.parcheu.feature.presentar.IntroResultScreen
import me.santiagoreina.parcheu.feature.presentar.IntroResultUiState
import me.santiagoreina.parcheu.feature.presentar.IntroduceActions
import me.santiagoreina.parcheu.feature.presentar.IntroduceContent
import me.santiagoreina.parcheu.feature.presentar.IntroduceScreen
import me.santiagoreina.parcheu.feature.presentar.SAMPLE_FRIENDS
import me.santiagoreina.parcheu.feature.presentar.introFullReport
import me.santiagoreina.parcheu.feature.presentar.introducerView
import me.santiagoreina.parcheu.feature.presentar.proposalView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de E1 (0, 1 y 2 elegidos y límite alcanzado), E2 (simulando, listo con el informe
 * gratis y con el completo, propuesta enviada y fallida) y E3 (pendiente, aceptado esperando,
 * rechazado y conectados), en ambos temas (ADR-0022). Grabar con `-Proborazzi.test.record=true`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class PresentarScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { ParcheUTheme(darkTheme = dark) { content() } }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/presentar/${name}_$theme.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    private fun e1(selected: List<String>, left: Int = 3) = capture(
        "e1_${selected.size}_of_2" + if (left == 0) "_limit" else ""
    ) {
        IntroduceScreen(
            IntroduceContent(friends = SAMPLE_FRIENDS, selected = selected, simulationsLeft = left),
            IntroduceActions()
        )
    }

    private fun e2(name: String, content: IntroResultContent) = capture("e2_$name") {
        IntroResultScreen(IntroResultUiState.Loaded(content), IntroResultActions())
    }

    private fun e3(name: String, content: IntroProposalContent) = capture("e3_$name") {
        IntroProposalScreen(IntroProposalUiState.Loaded(content), IntroProposalActions())
    }

    @Test
    fun e1None() = e1(emptyList())

    @Test
    fun e1One() = e1(listOf("u-vale"))

    @Test
    fun e1Two() = e1(listOf("u-vale", "u-sebas"))

    @Test
    fun e1Limit() = e1(listOf("u-vale", "u-sebas"), left = 0)

    @Test
    fun e2Simulating() =
        e2("simulating", IntroResultContent(introducerView(IntroductionStatus.SIMULATING)))

    @Test
    fun e2ReadyLocked() = e2(
        "ready_locked",
        IntroResultContent(
            introducerView(IntroductionStatus.READY),
            usedMonth = "octubre",
            resetMonth = "noviembre"
        )
    )

    @Test
    fun e2ReadyFull() = e2(
        "ready_full",
        IntroResultContent(
            introducerView(IntroductionStatus.READY, introFullReport()),
            plus = true
        )
    )

    @Test
    fun e2Sent() = e2(
        "sent",
        IntroResultContent(introducerView(IntroductionStatus.PROPOSED, introFullReport()))
    )

    @Test
    fun e2Failed() = e2("failed", IntroResultContent(introducerView(IntroductionStatus.FAILED)))

    @Test
    fun e3Pending() = e3("pending", IntroProposalContent(proposalView(report = introFullReport())))

    @Test
    fun e3Accepted() = e3("accepted_waiting", IntroProposalContent(proposalView(myResponse = true)))

    @Test
    fun e3Declined() = e3(
        "declined",
        IntroProposalContent(proposalView(IntroductionStatus.DECLINED, myResponse = false))
    )

    @Test
    fun e3Connected() = e3(
        "connected",
        IntroProposalContent(
            proposalView(
                IntroductionStatus.CONNECTED,
                myResponse = true,
                report = introFullReport("chat-9")
            )
        )
    )

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }
}
