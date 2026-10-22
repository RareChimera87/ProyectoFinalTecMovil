package me.santiagoreina.parcheu.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.contract.HelpResource
import me.santiagoreina.parcheu.contract.ReportReason
import me.santiagoreina.parcheu.designsystem.components.compound.ParcheUBottomSheetContent
import me.santiagoreina.parcheu.designsystem.components.compound.ParcheUModalDialogContent
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.safety.BlockConfirmBody
import me.santiagoreina.parcheu.feature.safety.BlockedBody
import me.santiagoreina.parcheu.feature.safety.ReportActions
import me.santiagoreina.parcheu.feature.safety.ReportScreen
import me.santiagoreina.parcheu.feature.safety.ReportSent
import me.santiagoreina.parcheu.feature.safety.ReportUiState
import me.santiagoreina.parcheu.feature.safety.SafetySheetActions
import me.santiagoreina.parcheu.feature.safety.SafetySheetBody
import me.santiagoreina.parcheu.feature.safety.SafetySheetState
import me.santiagoreina.parcheu.feature.safety.SafetyTarget
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de H1, H2, las confirmaciones de bloqueo y de reporte enviado, y las líneas de ayuda, en ambos
 * temas, contra las PNG de app/src/test/screenshots/safety. Grabar con `./gradlew :app:recordRoborazziDevDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class SafetyScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private val laura = SafetyTarget(id = "u2", name = "Laura Pinzón", chatId = "c1")

    private val help =
        listOf(
            HelpResource(
                "Línea 192, opción 4",
                "192",
                "Línea nacional de salud mental del Ministerio de Salud. Marca 192 y elige la opción 4.",
            ),
            HelpResource("Línea 106", "106", "Línea de escucha y orientación en crisis emocional."),
            HelpResource("Emergencias 123", "123", "Si tú o alguien más está en peligro inmediato."),
        )

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { ParcheUTheme(darkTheme = dark) { content() } }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/safety/${name}_$theme.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            ),
        )
    }

    /** Fondo con el scrim, como se ve la hoja o el diálogo sobre F9. */
    @Composable
    private fun OverScrim(alignment: Alignment, content: @Composable () -> Unit) {
        Box(
            Modifier.fillMaxSize().background(ParcheUTheme.colors.bg).background(ParcheUTheme.colors.scrim),
            contentAlignment = alignment,
        ) { content() }
    }

    @Test
    fun h1Sheet() = capture("h1_hoja") {
        OverScrim(Alignment.BottomCenter) {
            ParcheUBottomSheetContent { SafetySheetBody(SafetySheetState(laura), SafetySheetActions()) }
        }
    }

    @Test
    fun h1SheetAnonymousMuted() = capture("h1_hoja_anonima_silenciada") {
        OverScrim(Alignment.BottomCenter) {
            val anon = SafetyTarget("a_x", "Alguien de la UIS", anonymous = true, chatId = "c2", muted = true)
            ParcheUBottomSheetContent { SafetySheetBody(SafetySheetState(anon), SafetySheetActions()) }
        }
    }

    @Test
    fun blockConfirmation() = capture("confirmar_bloqueo") {
        OverScrim(Alignment.Center) {
            ParcheUModalDialogContent { BlockConfirmBody(SafetySheetState(laura), SafetySheetActions()) }
        }
    }

    @Test
    fun blockedConfirmation() = capture("bloqueo_hecho") {
        OverScrim(Alignment.Center) { ParcheUModalDialogContent { BlockedBody(laura) {} } }
    }

    @Test
    fun h2Reasons() = capture("h2_motivos") {
        ReportScreen(ReportUiState(laura, reason = ReportReason.HARASSMENT), ReportActions())
    }

    @Test
    fun reportSent() = capture("reporte_enviado") {
        ReportScreen(
            ReportUiState(laura, reason = ReportReason.HARASSMENT, sent = ReportSent(emptyList(), blocked = true)),
            ReportActions(),
        )
    }

    @Test
    fun reportSentWithHelpResources() = capture("reporte_enviado_recursos_de_ayuda") {
        ReportScreen(
            ReportUiState(laura, reason = ReportReason.SELF_HARM, sent = ReportSent(help, blocked = false)),
            ReportActions(),
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
