package me.santiagoreina.parcheu.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.data.results.lockedReport
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.home.HomeCallbacks
import me.santiagoreina.parcheu.feature.home.HomeSamples
import me.santiagoreina.parcheu.feature.home.HomeScreen
import me.santiagoreina.parcheu.feature.parche.ParcheCallbacks
import me.santiagoreina.parcheu.feature.parche.ParcheSamples
import me.santiagoreina.parcheu.feature.parche.ParcheScreen
import me.santiagoreina.parcheu.feature.parche.ParcheUiState
import me.santiagoreina.parcheu.feature.plus.PaywallActions
import me.santiagoreina.parcheu.feature.plus.PaywallScreen
import me.santiagoreina.parcheu.feature.plus.PaywallUiState
import me.santiagoreina.parcheu.feature.plus.PlansUi
import me.santiagoreina.parcheu.feature.plus.PlusTestData
import me.santiagoreina.parcheu.feature.results.DecisionState
import me.santiagoreina.parcheu.feature.results.ResultsActions
import me.santiagoreina.parcheu.feature.results.ResultsContent
import me.santiagoreina.parcheu.feature.results.ResultsScreen
import me.santiagoreina.parcheu.feature.results.ResultsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * E10.4: C1, D1, F7 y G1 con `fontScale = 1,3` en ambos temas. Cada captura se versiona (regresión,
 * `changeThreshold` 0,01) y además se verifica por semántica que ningún texto se recorte, que las
 * áreas táctiles midan al menos 48dp y que todo elemento accionable tenga etiqueta para TalkBack.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class FontScaleScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private fun capture(name: String, content: @Composable () -> Unit) {
        var fontScale by mutableFloatStateOf(1f)
        composeRule.setContent {
            ParcheUTheme(darkTheme = dark) {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density, fontScale = fontScale)
                ) { content() }
            }
        }
        composeRule.waitForIdle()
        // Línea base con escala 1,0: la elipsis que ya existe ahí es diseño; la nueva con 1,3 es pérdida.
        val baselineEllipsis = A11yAssertions.ellipsizedTexts(composeRule)
        fontScale = FONT_SCALE
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/fontscale/${name}_$theme.png",
            roborazziOptions =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
        A11yAssertions.assertNoClippedText(composeRule, name, baselineEllipsis)
        A11yAssertions.assertTouchTargets(composeRule, name)
        A11yAssertions.assertActionsLabeled(composeRule, name)
    }

    @Test
    fun c1Home() = capture("c1_inicio") {
        HomeScreen(
            HomeSamples.freeThreeOfFive,
            HomeCallbacks(),
            now = HomeSamples.now,
            animate = false
        )
    }

    @Test
    fun d1Graph() = capture("d1_grafo") {
        ParcheScreen(
            ParcheUiState.Loaded(ParcheSamples.content),
            ParcheCallbacks(),
            now = Instant.parse("2026-11-04T11:00:00Z"),
            animate = false
        )
    }

    @Test
    fun f7Locked() = capture("f7_informe_gratis") {
        ResultsScreen(
            ResultsUiState.Loaded(
                ResultsContent(
                    otherLabel = "Alguien de la UIS",
                    mode = Mode.ESTUDIO,
                    isDemo = false,
                    plus = false,
                    report = lockedReport(true),
                    decision = DecisionState.PENDING,
                    usedMonth = "octubre",
                    resetMonth = "noviembre"
                )
            ),
            ResultsActions()
        )
    }

    @Test
    fun g1Paywall() = capture("g1_paywall") {
        PaywallScreen(
            PaywallUiState(plans = PlansUi.Loaded(PlusTestData.plans)),
            PaywallActions()
        )
    }

    companion object {
        const val FONT_SCALE = 1.3f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }
}
