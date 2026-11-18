package me.santiagoreina.parcheu.feature.chat

import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.theme.DarkParcheUColors
import me.santiagoreina.parcheu.designsystem.theme.LightParcheUColors
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.designsystem.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Botón de enviar de F9: contraste en ambos temas, zona táctil, etiqueta y estados. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class SendButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun habilitadoContrastaConElFondoYDeshabilitadoSeDistingue() {
        listOf(
            "claro" to LightParcheUColors,
            "oscuro" to DarkParcheUColors
        ).forEach { (theme, colors) ->
            val on = sendButtonColors(colors, canSend = true)
            val off = sendButtonColors(colors, canSend = false)
            // Componente gráfico (WCAG 1.4.11): el relleno contra el fondo y el icono contra el relleno.
            val fill = contrastRatio(on.container, colors.bg)
            val icon = contrastRatio(on.icon, on.container)
            assertTrue("$theme relleno/bg = $fill", fill >= 3.0)
            assertTrue("$theme icono/relleno = $icon", icon >= 4.5)
            val offIcon = contrastRatio(off.icon, off.container)
            assertTrue("$theme icono deshabilitado = $offIcon", offIcon >= 3.0)
            assertNotEquals(theme, on.container, off.container)
        }
    }

    @Test
    fun conTextoSeEnviaYVacioNo() {
        var sent = 0
        composeRule.setContent {
            ParcheUTheme(darkTheme = false) {
                ChatInputBar("Hola", {
                }, { sent++ }, canSend = true, enabled = true, placeholder = "Escribe")
            }
        }
        composeRule.onNodeWithTag(ChatTags.SEND)
            .assertIsEnabled()
            .assertContentDescriptionContains("Enviar")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, sent)
    }

    @Test
    fun vacioQuedaDeshabilitado() {
        composeRule.setContent {
            ParcheUTheme(darkTheme = false) {
                ChatInputBar("", {}, {}, canSend = false, enabled = true, placeholder = "Escribe")
            }
        }
        composeRule.onNodeWithTag(ChatTags.SEND)
            .assertIsNotEnabled()
            .assertContentDescriptionContains("Enviar")
    }
}
