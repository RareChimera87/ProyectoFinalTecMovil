package me.santiagoreina.parcheu.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.contract.TwinPoint
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.profile.ProfileCallbacks
import me.santiagoreina.parcheu.feature.profile.ProfileSamples
import me.santiagoreina.parcheu.feature.profile.ProfileScreen
import me.santiagoreina.parcheu.feature.profile.ProfileUiState
import me.santiagoreina.parcheu.feature.profile.chartOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de C3 con 0, 1 y 16 puntos (4 meses de historial, datos demo) en ambos temas (E9.1).
 * Regresión contra app/src/test/screenshots/profile.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class ProfileScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private fun capture(name: String, points: List<TwinPoint>) {
        val state =
            ProfileUiState.Loaded(
                ProfileSamples.me,
                ProfileSamples.twin,
                chartOf(points),
                "Universidad Industrial de Santander"
            )
        composeRule.setContent {
            ParcheUTheme(darkTheme = dark) { ProfileScreen(state, ProfileCallbacks()) }
        }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/profile/${name}_$theme.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    @Test
    fun c3ZeroPoints() = capture("c3_0_points", ProfileSamples.zero)

    @Test
    fun c3OnePoint() = capture("c3_1_point", ProfileSamples.one)

    @Test
    fun c3SixteenPoints() = capture("c3_16_points", ProfileSamples.sixteen)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
