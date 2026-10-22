package me.santiagoreina.parcheu.designsystem.components.compound

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** LineChart no debe caerse con pocos datos ni con valores no finitos (C3 con una sola medición). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class LineChartTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun render(points: List<Float>) {
        composeRule.setContent {
            ParcheUTheme { LineChart(points = points, contentDescription = "grafica") }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("grafica").assertIsDisplayed()
    }

    @Test fun sinPuntos() = render(emptyList())

    @Test fun unPunto() = render(listOf(72f))

    @Test fun dosPuntos() = render(listOf(40f, 80f))

    @Test fun valoresNoFinitos() = render(listOf(Float.NaN, 50f, Float.POSITIVE_INFINITY))

    @Test fun soloNoFinitos() = render(listOf(Float.NaN, Float.NEGATIVE_INFINITY))
}
