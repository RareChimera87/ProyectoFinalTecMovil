package me.santiagoreina.parcheu.designsystem.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas Roborazzi de G0 en sus seis estados (todos, filtrado, seleccionado, fof, miniatura y count
 * reducido) y ambos temas. Regresión con `changeThreshold` 0,01 (ADR-0022).
 * Grabar: `./gradlew :designsystem:recordRoborazziDebug`; verificar: `:designsystem:verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class PuGraphScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val all = PuGraphSamples.nodes

    @Test fun allDark() = capture("g0_todos_dark", true) { PuGraph(all) }

    @Test fun allLight() = capture("g0_todos_light", false) { PuGraph(all) }

    @Test fun filteredDark() =
        capture("g0_filtrado_dark", true) { PuGraph(all, modeFilter = GraphMode.AMISTAD) }

    @Test fun filteredLight() =
        capture("g0_filtrado_light", false) { PuGraph(all, modeFilter = GraphMode.AMISTAD) }

    @Test fun selectedDark() = capture("g0_seleccionado_dark", true) {
        PuGraph(all, selectedId = "lau")
    }

    @Test fun selectedLight() =
        capture("g0_seleccionado_light", false) { PuGraph(all, selectedId = "lau") }

    @Test fun fofDark() = capture("g0_fof_dark", true) { PuGraph(all, fof = PuGraphSamples.fof) }

    @Test fun fofLight() = capture("g0_fof_light", false) { PuGraph(all, fof = PuGraphSamples.fof) }

    @Test fun miniDark() = capture("g0_miniatura_dark", true) { PuGraph(all, size = MINI) }

    @Test fun miniLight() = capture("g0_miniatura_light", false) { PuGraph(all, size = MINI) }

    @Test fun reducedDark() = capture("g0_count_reducido_dark", true) { PuGraph(all.take(4)) }

    @Test fun reducedLight() = capture("g0_count_reducido_light", false) { PuGraph(all.take(4)) }

    private fun capture(name: String, darkTheme: Boolean, content: @Composable () -> Unit) {
        composeRule.setContent {
            ParcheUTheme(darkTheme = darkTheme) {
                Box(Modifier.background(ParcheUTheme.colors.bg).padding(16.dp)) { content() }
            }
        }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/$name.png",
            roborazziOptions = ROBORAZZI_OPTIONS
        )
    }

    companion object {
        private val MINI: Dp = 160.dp
        private val ROBORAZZI_OPTIONS =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
    }
}
