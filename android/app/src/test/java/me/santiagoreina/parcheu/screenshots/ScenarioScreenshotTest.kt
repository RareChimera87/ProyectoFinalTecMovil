package me.santiagoreina.parcheu.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.ScenarioSessionDto
import me.santiagoreina.parcheu.contract.ScenarioSessionStatus
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.chat.ChatActions
import me.santiagoreina.parcheu.feature.chat.ChatHeader
import me.santiagoreina.parcheu.feature.chat.ChatMessageUi
import me.santiagoreina.parcheu.feature.chat.ChatScreen
import me.santiagoreina.parcheu.feature.chat.ChatUiState
import me.santiagoreina.parcheu.feature.conocer.scenario.ScenarioAgreementBar
import me.santiagoreina.parcheu.feature.conocer.scenario.ScenarioPinnedCard
import me.santiagoreina.parcheu.feature.conocer.scenario.ScenarioUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de F3 (E4.8) en ambos temas: en curso con temporizador y presupuesto, con la otra
 * persona esperando
 * el acuerdo, a la espera de ella, y evaluando. PNG en app/src/test/screenshots/scenario.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class ScenarioScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private fun capture(name: String, content: @Composable () -> Unit) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        composeRule.setContent { ParcheUTheme(darkTheme = dark) { content() } }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/scenario/${name}_$theme.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    private val header =
        ChatHeader(
            "c1",
            "Alguien de la UIS",
            "AU",
            true,
            null,
            Mode.AMISTAD,
            muted = false,
            closed = false,
            otherAnonId = "h7Kq2xP9"
        )
    private val at = Instant.fromEpochSeconds(1_761_130_000)
    private val chat =
        ChatUiState(
            loading = false,
            header = header,
            messages = listOf(
                ChatMessageUi(
                    "m1",
                    "¿Y si vamos al Chicamocha el domingo?",
                    mine = false,
                    createdAt = at
                ),
                ChatMessageUi(
                    "m2",
                    "Hágale: bus $70.000, entrada $50.000 y mercado $30.000",
                    mine = true,
                    createdAt = at
                )
            )
        )

    private fun session(
        status: ScenarioSessionStatus,
        iAgreed: Boolean = false,
        otherAgreed: Boolean = false
    ) = ScenarioUiState(
        session = ScenarioSessionDto(
            status = status,
            title = "Planear el fin de semana",
            prompt = "Tienen un fin de semana libre en Bucaramanga. " +
                "Pónganse de acuerdo en un plan para el sábado.",
            budgetCop = 150_000,
            endsAt = Instant.fromEpochSeconds(1_761_130_300),
            iAgreed = iAgreed,
            otherAgreed = otherAgreed
        ),
        remainingSec = 272,
        totalSec = 600
    )

    @Composable
    private fun F3(state: ScenarioUiState, closed: Boolean = false) {
        Column(Modifier.fillMaxSize()) {
            ChatScreen(
                state = chat.copy(header = header.copy(closed = closed)),
                actions = ChatActions(),
                pinned = { ScenarioPinnedCard(state) },
                footer = {
                    ScenarioAgreementBar(state, onAgree = {}, onOpenResults = {})
                }
            )
        }
    }

    @Test
    fun inProgress() = capture("f3_en_curso") { F3(session(ScenarioSessionStatus.OPEN)) }

    @Test
    fun otherProposedAgreement() = capture("f3_otro_propone") {
        F3(session(ScenarioSessionStatus.OPEN, otherAgreed = true))
    }

    @Test
    fun waitingOther() = capture("f3_esperando_acuerdo") {
        F3(session(ScenarioSessionStatus.WAITING_OTHER, iAgreed = true))
    }

    @Test
    fun judging() = capture("f3_evaluando") {
        F3(session(ScenarioSessionStatus.JUDGING, true, true), closed = true)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }
}
