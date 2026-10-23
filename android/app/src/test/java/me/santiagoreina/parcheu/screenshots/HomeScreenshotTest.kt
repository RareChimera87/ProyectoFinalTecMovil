package me.santiagoreina.parcheu.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.home.HomeCallbacks
import me.santiagoreina.parcheu.feature.home.HomeSamples
import me.santiagoreina.parcheu.feature.home.HomeScreen
import me.santiagoreina.parcheu.feature.home.HomeUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de C1 (3 de 5, en curso, resultado listo, vacío, 0 restantes, ParcheU+ y perfiles demo) en oscuro, e I1
 * (las mismas en claro, ADR-0022). Se graban con `-Proborazzi.test.record=true`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class HomeScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    /** C1 en oscuro; I1 es la versión clara. */
    private val prefix = if (dark) "c1" else "i1"

    private fun capture(name: String, state: HomeUiState) {
        composeRule.setContent {
            ParcheUTheme(darkTheme = dark) {
                HomeScreen(state, HomeCallbacks(), now = HomeSamples.now, animate = false)
            }
        }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/home/${prefix}_$name.png",
            roborazziOptions =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    @Test
    fun threeOfFive() = capture("3_de_5", HomeSamples.freeThreeOfFive)

    @Test
    fun inProgress() = capture("en_curso", HomeSamples.inProgress)

    @Test
    fun resultReady() = capture("resultado_listo", HomeSamples.resultReady)

    @Test
    fun empty() = capture("vacio", HomeSamples.empty)

    @Test
    fun noneLeft() = capture("0_restantes", HomeSamples.noneLeft)

    @Test
    fun plus() = capture("plus", HomeSamples.plus)

    @Test
    fun demo() = capture("demo", HomeSamples.demo)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }
}
