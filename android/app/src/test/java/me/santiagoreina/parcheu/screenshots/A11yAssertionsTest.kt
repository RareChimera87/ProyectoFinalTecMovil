package me.santiagoreina.parcheu.screenshots

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Comprueba que las verificaciones de E10.4 detectan de verdad los problemas que dicen cubrir. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class A11yAssertionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun detectsTextClippedInHeight() {
        composeRule.setContent {
            Box(Modifier.width(80.dp).height(10.dp)) {
                Text("Un texto largo que ocupa varias líneas y no cabe en la caja")
            }
        }
        assertThrows(AssertionError::class.java) {
            A11yAssertions.assertNoClippedText(composeRule, "recortado")
        }
    }

    @Test
    fun acceptsTextThatFits() {
        composeRule.setContent { Text("Hola") }
        A11yAssertions.assertNoClippedText(composeRule, "cabe")
    }

    @Test
    fun detectsTextClippedByContainer() {
        // El Text mide con restricciones holgadas, así que su TextLayoutResult no desborda; lo recorta el
        // padre de alto fijo con clipToBounds.
        composeRule.setContent {
            Box(Modifier.width(200.dp).height(8.dp).clipToBounds()) {
                Text("Ver ParcheU+", modifier = Modifier.wrapContentHeight(unbounded = true))
            }
        }
        assertThrows(AssertionError::class.java) {
            A11yAssertions.assertNoClippedText(composeRule, "clip de contenedor")
        }
    }

    @Test
    fun acceptsTextInsideScrollable() {
        composeRule.setContent {
            Column(Modifier.height(20.dp).verticalScroll(rememberScrollState())) {
                repeat(10) { Text("Fila $it") }
            }
        }
        A11yAssertions.assertNoClippedText(composeRule, "scroll")
    }

    @Test
    fun detectsNewEllipsisButAcceptsBaseline() {
        val text = "Un texto largo que ocupa varias líneas y se trunca con elipsis siempre"
        composeRule.setContent {
            Text(
                text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(60.dp)
            )
        }
        assertThrows(AssertionError::class.java) {
            A11yAssertions.assertNoClippedText(composeRule, "elipsis nueva")
        }
        assertEquals(setOf(text), A11yAssertions.ellipsizedTexts(composeRule))
        A11yAssertions.assertNoClippedText(composeRule, "línea base", setOf(text))
    }

    @Test
    fun detectsSquashedButtonDespiteAutomaticTouchExpansion() {
        // Sin desactivar la ampliación automática: un botón de 7dp se ve aplastado.
        composeRule.setContent {
            Box(Modifier.size(width = 200.dp, height = 7.dp).clickable {})
        }
        assertThrows(AssertionError::class.java) {
            A11yAssertions.assertTouchTargets(composeRule, "aplastado")
        }
    }

    @Test
    fun detectsSmallTouchTarget() {
        // Compose amplía por sí solo el área táctil a 48dp; se desactiva para simular un componente
        // que la recorta (por ejemplo, con una `ViewConfiguration` propia).
        composeRule.setContent {
            val base = LocalViewConfiguration.current
            val noMin =
                object : ViewConfiguration by base {
                    override val minimumTouchTargetSize = DpSize.Zero
                }
            CompositionLocalProvider(LocalViewConfiguration provides noMin) {
                Box(Modifier.size(24.dp).clickable {})
            }
        }
        assertThrows(AssertionError::class.java) {
            A11yAssertions.assertTouchTargets(composeRule, "pequeño")
        }
    }

    @Test
    fun detectsUnlabeledAction() {
        composeRule.setContent { Box(Modifier.size(48.dp).clickable {}) }
        assertThrows(AssertionError::class.java) {
            A11yAssertions.assertActionsLabeled(composeRule, "sin etiqueta")
        }
    }
}
