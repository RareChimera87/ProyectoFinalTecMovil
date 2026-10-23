package me.santiagoreina.parcheu.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.parche.fof.FofCallbacks
import me.santiagoreina.parcheu.feature.parche.fof.FofSamples
import me.santiagoreina.parcheu.feature.parche.fof.FofScreen
import me.santiagoreina.parcheu.feature.parche.fof.FofUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de D6 (ParcheU+, con la capa fof de G0) y D7 (gratis, borroso con candado) en ambos temas
 * (ADR-0022, E6.3). Se graban con `-Proborazzi.test.record=true`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class FofScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private fun capture(name: String, state: FofUiState) {
        composeRule.setContent {
            ParcheUTheme(darkTheme = dark) {
                FofScreen(state, FofCallbacks(), animate = false)
            }
        }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/parche/${name}_$theme.png",
            roborazziOptions =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    @Test
    fun d6FriendsOfFriends() = capture("d6_amigos_de_amigos", FofUiState.Unlocked(FofSamples.plus))

    @Test
    fun d6Empty() = capture("d6_vacio", FofUiState.Unlocked(FofSamples.plusEmpty))

    @Test
    fun d7Locked() = capture("d7_gratis", FofUiState.Locked(12))

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }
}
