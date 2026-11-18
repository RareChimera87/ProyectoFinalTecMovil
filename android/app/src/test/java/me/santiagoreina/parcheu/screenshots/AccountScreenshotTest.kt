package me.santiagoreina.parcheu.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.time.Instant
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.deleteaccount.DeleteAccountActions
import me.santiagoreina.parcheu.feature.deleteaccount.DeleteAccountScreen
import me.santiagoreina.parcheu.feature.deleteaccount.DeleteAccountTags
import me.santiagoreina.parcheu.feature.deleteaccount.DeleteAccountUiState
import me.santiagoreina.parcheu.feature.deleteaccount.DeletePlusNotice
import me.santiagoreina.parcheu.feature.exportdata.ExportDataActions
import me.santiagoreina.parcheu.feature.exportdata.ExportDataScreen
import me.santiagoreina.parcheu.feature.exportdata.ExportDataUiState
import me.santiagoreina.parcheu.feature.exportdata.ExportOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de la confirmación de borrado, la cuenta borrada y 'Descargar mis datos' (E8.3, pantallas nuevas
 * sin mockup) en ambos temas, contra las PNG de app/src/test/screenshots/account. Grabar con
 * `./gradlew :app:testDevDebugUnitTest -Proborazzi.test.record=true`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class AccountScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private fun capture(
        name: String,
        scrollToTag: String? = null,
        content: @Composable () -> Unit
    ) {
        composeRule.setContent { ParcheUTheme(darkTheme = dark) { content() } }
        scrollToTag?.let { composeRule.onNodeWithTag(it).performScrollTo() }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/account/${name}_$theme.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    @Test
    fun deleteConfirmation() = capture("borrar_cuenta") {
        DeleteAccountScreen(DeleteAccountUiState(), DeleteAccountActions())
    }

    @Test
    fun deleteConfirmationChecked() = capture("borrar_cuenta_confirmada") {
        DeleteAccountScreen(DeleteAccountUiState(confirmed = true), DeleteAccountActions())
    }

    /** El final del contenido desplazable: el aviso de ParcheU+ y "Descargar mis datos" sobre el pie fijo. */
    @Test
    fun deleteConfirmationScrolledToTheEnd() =
        capture("borrar_cuenta_final", scrollToTag = DeleteAccountTags.DOWNLOAD_FIRST) {
            DeleteAccountScreen(DeleteAccountUiState(), DeleteAccountActions())
        }

    /** ParcheU+ concedido sin Play: el aviso dice cuándo vence y no manda a Google Play. */
    @Test
    fun deleteConfirmationWithPlusWithoutPlay() =
        capture("borrar_cuenta_plus_sin_play", scrollToTag = DeleteAccountTags.DOWNLOAD_FIRST) {
            DeleteAccountScreen(
                DeleteAccountUiState(
                    plusNotice = DeletePlusNotice.Granted(Instant.parse("2026-12-12T17:00:00Z"))
                ),
                DeleteAccountActions()
            )
        }

    @Test
    fun accountDeleted() = capture("cuenta_borrada") {
        DeleteAccountScreen(DeleteAccountUiState(deleted = true), DeleteAccountActions())
    }

    @Test
    fun exportData() = capture("descargar_datos") {
        ExportDataScreen(ExportDataUiState(), ExportDataActions())
    }

    @Test
    fun exportDataSaved() = capture("descargar_datos_guardado") {
        ExportDataScreen(ExportDataUiState(outcome = ExportOutcome.SAVED), ExportDataActions())
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
