package me.santiagoreina.parcheu.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.parche.communities.CommunitiesCallbacks
import me.santiagoreina.parcheu.feature.parche.communities.CommunitiesSamples
import me.santiagoreina.parcheu.feature.parche.communities.CommunitiesScreen
import me.santiagoreina.parcheu.feature.parche.communities.CommunitiesUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de D8 en sus 3 estados (con comunidades, bloqueado y vacío) en ambos temas (ADR-0022, E6.4).
 * Se graban con `-Proborazzi.test.record=true`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class CommunitiesScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private fun capture(name: String, state: CommunitiesUiState) {
        composeRule.setContent {
            ParcheUTheme(darkTheme = dark) {
                CommunitiesScreen(state, CommunitiesCallbacks(), animate = false)
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
    fun d8WithCommunities() =
        capture("d8_comunidades", CommunitiesUiState.Loaded(CommunitiesSamples.plus))

    @Test
    fun d8Locked() = capture("d8_bloqueado", CommunitiesUiState.Locked(2))

    @Test
    fun d8Empty() = capture("d8_vacio", CommunitiesUiState.Empty)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }
}
