package me.santiagoreina.parcheu.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.traintwin.TrainTwinCallbacks
import me.santiagoreina.parcheu.feature.traintwin.TrainTwinContent
import me.santiagoreina.parcheu.feature.traintwin.TrainTwinSamples
import me.santiagoreina.parcheu.feature.traintwin.TrainTwinScreen
import me.santiagoreina.parcheu.feature.traintwin.TrainTwinUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de C4 con una memoria pendiente, una aceptada y una respuesta por calificar, en ambos temas
 * (E9.3). Regresión contra app/src/test/screenshots/traintwin.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h1000dp-xhdpi")
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class TrainTwinScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private fun capture(name: String, content: TrainTwinContent) {
        composeRule.setContent {
            ParcheUTheme(darkTheme = dark) {
                TrainTwinScreen(TrainTwinUiState.Loaded(content), TrainTwinCallbacks())
            }
        }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/traintwin/${name}_$theme.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    @Test
    fun c4PendingAcceptedAndRating() = capture(
        "c4_pending_accepted_rating",
        TrainTwinContent(
            pending = listOf(TrainTwinSamples.pending),
            accepted = listOf(TrainTwinSamples.accepted),
            prompts = listOf(TrainTwinSamples.control, TrainTwinSamples.rating)
        )
    )

    @Test
    fun c4Empty() = capture("c4_empty", TrainTwinContent())

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
