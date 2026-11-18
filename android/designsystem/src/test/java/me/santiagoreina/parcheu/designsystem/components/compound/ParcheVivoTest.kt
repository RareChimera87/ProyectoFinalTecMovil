package me.santiagoreina.parcheu.designsystem.components.compound

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.santiagoreina.parcheu.designsystem.brand.ParcheULogo
import me.santiagoreina.parcheu.designsystem.components.base.ModeSpineCard
import me.santiagoreina.parcheu.designsystem.theme.LightParcheUColors
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ParcheVivoTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun quotaMascotsIsOneNodeWithItsDescription() {
        composeRule.setContent {
            ParcheUTheme { QuotaMascots(used = 3, limit = 5, modifier = Modifier.testTag("q")) }
        }
        composeRule.onNodeWithTag("q")
            .assertContentDescriptionEquals("Te quedan 2 de 5 simulaciones esta semana")
    }

    @Test
    fun quotaMascotsDescriptions() {
        assertEquals("Te quedan 0 de 5 simulaciones esta semana", quotaMascotsDescription(7, 5))
        assertEquals("Simulaciones sin límite con ParcheU+", quotaMascotsDescription(4, null))
    }

    @Test
    fun heroTitleIsAHeadingWithItsSuffix() {
        composeRule.setContent {
            ParcheUTheme { HeroBlock(title = "2", titleSuffix = "libres de 5", overline = "Cupo") }
        }
        composeRule.onNodeWithText("2 libres de 5")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText(
            "CUPO"
        ).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading))
    }

    @Test
    fun spineCardWithClickIsAButton() {
        var clicks = 0
        composeRule.setContent {
            ParcheUTheme {
                ModeSpineCard(
                    mode = ParcheUTheme.colors.study,
                    onClick = { clicks++ },
                    modifier = Modifier.testTag("c")
                ) {}
            }
        }
        composeRule.onNodeWithTag("c").assertHasClickAction().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun logoIsReadAsParcheU() {
        composeRule.setContent { ParcheUTheme { ParcheULogo(Modifier.testTag("l")) } }
        composeRule.onNodeWithTag("l").assertContentDescriptionEquals("ParcheU")
    }

    @Test
    fun duotoneMapsBlackToShadowAndWhiteToHighlight() {
        val shadow = Color(0xFF14111F)
        val highlight = LightParcheUColors.study.solid
        val m = duotoneColorMatrix(shadow, highlight).values
        // Negro: solo cuenta la columna de desplazamiento (0..255).
        assertEquals(shadow.red * 255f, m[4], 0.01f)
        assertEquals(shadow.blue * 255f, m[14], 0.01f)
        // Blanco: suma de la fila + desplazamiento = luz.
        val whiteR = (m[0] + m[1] + m[2]) * 255f + m[4]
        assertEquals(highlight.red * 255f, whiteR, 0.5f)
        // Alfa intacto.
        assertEquals(1f, m[18], 0f)
    }
}
