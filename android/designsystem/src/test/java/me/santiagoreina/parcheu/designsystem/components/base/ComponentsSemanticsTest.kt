package me.santiagoreina.parcheu.designsystem.components.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Criterio de E1.3: todo elemento interactivo mide al menos 48 x 48 dp y expone rol y descripción
 * (texto visible o contentDescription) a los servicios de accesibilidad. Se recorre cada galería en
 * ambos temas, que incluye los estados habilitado, deshabilitado y seleccionado.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h2400dp-xhdpi")
class ComponentsSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun buttons() = check("buttons") { Galleries.Buttons() }

    @Test fun chips() = check("chips") { Galleries.Chips() }

    @Test fun segmented() = check("segmented") { Galleries.Segmented() }

    @Test fun selection() = check("selection") { Galleries.Selection() }

    @Test fun fields() = check("fields") { Galleries.Fields() }

    @Test fun cards() = check("cards") { Galleries.Cards() }

    @Test fun bars() = check("bars") { Galleries.Bars() }

    @Test fun navBar() = check("navbar") { Galleries.NavBars() }

    private fun check(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { ParcheUTheme { content() } }
        composeRule.waitForIdle()
        val density = composeRule.density.density
        val nodes = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("La galería $name no tiene elementos interactivos", nodes.isNotEmpty())
        nodes.forEach { node ->
            val widthDp = node.size.width / density
            val heightDp = node.size.height / density
            val id = "$name#${node.id}"
            assertTrue(
                "$id mide $widthDp x $heightDp dp (< 48)",
                widthDp >= 47.99f && heightDp >= 47.99f,
            )

            val editable = node.config.getOrNull(SemanticsProperties.EditableText) != null
            val role = node.config.getOrNull(SemanticsProperties.Role)
            assertTrue("$id no declara rol", role != null || editable)

            val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
            val description = node.config.getOrNull(
                SemanticsProperties.ContentDescription,
            ).orEmpty()
            assertTrue(
                "$id no tiene texto ni contentDescription",
                texts.any { it.text.isNotBlank() } || description.any { it.isNotBlank() } ||
                    editable,
            )
        }
    }
}
