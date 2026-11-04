package me.santiagoreina.parcheu.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.parche.NodeSheet
import me.santiagoreina.parcheu.feature.parche.ParcheCallbacks
import me.santiagoreina.parcheu.feature.parche.ParcheFilters
import me.santiagoreina.parcheu.feature.parche.ParcheSamples
import me.santiagoreina.parcheu.feature.parche.ParcheScreen
import me.santiagoreina.parcheu.feature.parche.ParcheUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de D1-D5, las tres variantes de D2 (conectados, simularon, pendiente) e I2 (D1 en claro) en
 * ambos temas (ADR-0022). Se graban con `./gradlew :app:recordRoborazziDevDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class ParcheScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"
    private val now = Instant.parse("2026-11-04T11:00:00Z")

    private fun capture(name: String, state: ParcheUiState) {
        composeRule.setContent {
            ParcheUTheme(darkTheme = dark) {
                Screen(state)
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

    @Composable
    private fun Screen(state: ParcheUiState) {
        ParcheScreen(state, ParcheCallbacks(), now = now, animate = false)
    }

    private fun sheetFor(id: String, withSimulation: Boolean = true) =
        ParcheSamples.nodes.first { it.id == id }.let { node ->
            NodeSheet(
                node = node,
                detail = ParcheSamples.detail(node, withSimulation),
                loadingDetail = false
            )
        }

    private val loaded = ParcheUiState.Loaded(ParcheSamples.content)

    @Test
    fun d1Graph() = capture("d1_grafo", loaded)

    @Test
    fun d1ModeFilter() = capture(
        "d1_filtro_estudio",
        loaded.copy(filters = ParcheFilters(mode = Mode.ESTUDIO))
    )

    @Test
    fun d1AffinityFilter() = capture(
        "d1_filtro_afinidad",
        loaded.copy(filters = ParcheFilters(minAffinity = true))
    )

    @Test
    fun d1Plus() = capture(
        "d1_plus",
        ParcheUiState.Loaded(ParcheSamples.content.copy(plus = true))
    )

    @Test
    fun d2Connected() = capture("d2_conectados", loaded.copy(sheet = sheetFor("and")))

    @Test
    fun d2Simulated() = capture("d2_simularon", loaded.copy(sheet = sheetFor("dan")))

    @Test
    fun d2Pending() = capture("d2_pendiente", loaded.copy(sheet = sheetFor("mat")))

    @Test
    fun d2Demo() = capture(
        "d2_demo",
        loaded.copy(sheet = sheetFor("and").let { it.copy(node = it.node.copy(isDemo = true)) })
    )

    @Test
    fun d3Empty() = capture("d3_vacio", ParcheUiState.Empty)

    @Test
    fun d4Loading() = capture("d4_cargando", ParcheUiState.Loading)

    @Test
    fun d5Error() = capture("d5_error", ParcheUiState.Error(AppError.Network))

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }
}
