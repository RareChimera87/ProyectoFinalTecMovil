package me.santiagoreina.parcheu.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.navigation.ParcheUNavBar
import me.santiagoreina.parcheu.navigation.TopLevelDestination
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** N0 en cada raíz (Inicio, Parche, Conocer, Chats, Perfil) activa, en tema oscuro y claro. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class NavBarScreenshotTest(private val active: TopLevelDestination, private val dark: Boolean) {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun capture() {
        rule.setContent {
            ParcheUTheme(darkTheme = dark) { ParcheUNavBar(active = active, onSelect = {}) }
        }
        val theme = if (dark) "dark" else "light"
        rule.onNodeWithTag("navbar").captureRoboImage(
            filePath = "src/test/screenshots/navbar/navbar_${active.name.lowercase()}_$theme.png",
            roborazziOptions =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-dark={1}")
        fun params(): List<Array<Any>> = TopLevelDestination.entries.flatMap {
            listOf(arrayOf<Any>(it, true), arrayOf<Any>(it, false))
        }
    }
}
