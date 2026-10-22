package me.santiagoreina.parcheu.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.contract.IsoDate
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.basicprofile.BasicProfileForm
import me.santiagoreina.parcheu.feature.basicprofile.BasicProfileSamples
import me.santiagoreina.parcheu.feature.basicprofile.BasicProfileScreen
import me.santiagoreina.parcheu.feature.basicprofile.BasicProfileUiState
import me.santiagoreina.parcheu.feature.underage.UnderageScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de B0 'Tu perfil' (vacío, completo, modo edición, error al guardar) y del bloqueo por edad
 * ('error de menor') en ambos temas (ADR-0022). B0 no tiene mockup: I2 las revisa contra el sistema de
 * diseño. Grabar con `./gradlew :app:recordRoborazziDevDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class BasicProfileScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { ParcheUTheme(darkTheme = dark) { content() } }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/basicprofile/${name}_$theme.png",
            roborazziOptions =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            ),
        )
    }

    @Composable
    private fun B0(state: BasicProfileUiState) {
        BasicProfileScreen(
            state = state,
            onPickBirthDate = {},
            onPickUniversity = {},
            onCareerChange = {},
            onDisplayNameChange = {},
            onInterestToggle = {},
            onSubmit = {},
            onRetryLoad = {},
            onBack = {},
            animateLoading = false,
        )
    }

    @Test
    fun b0Empty() = capture("b0_vacio") {
        B0(BasicProfileUiState.Form(BasicProfileSamples.catalog, BasicProfileForm(), edit = false))
    }

    @Test
    fun b0Complete() = capture("b0_completo") {
        B0(BasicProfileUiState.Form(BasicProfileSamples.catalog, BasicProfileSamples.complete, edit = false))
    }

    @Test
    fun b0Edit() = capture("b0_edicion") {
        B0(
            BasicProfileUiState.Form(
                BasicProfileSamples.catalog,
                BasicProfileSamples.complete.copy(birthDateLocked = true),
                edit = true,
            ),
        )
    }

    @Test
    fun b0SaveError() = capture("b0_error_conexion") {
        B0(
            BasicProfileUiState.Form(
                BasicProfileSamples.catalog,
                BasicProfileSamples.complete.copy(birthDate = IsoDate("2003-01-09")),
                edit = false,
                error = AppError.Network,
            ),
        )
    }

    @Test
    fun b0Underage() = capture("b0_error_menor") {
        UnderageScreen(onDeleteAccount = {}, onSignOut = {})
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
