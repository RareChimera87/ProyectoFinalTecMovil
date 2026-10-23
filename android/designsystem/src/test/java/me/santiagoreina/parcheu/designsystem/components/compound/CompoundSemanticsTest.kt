package me.santiagoreina.parcheu.designsystem.components.compound

import androidx.compose.runtime.Composable
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
 * Todo elemento interactivo de los compuestos mide al menos 48 x 48 dp y expone rol y descripción
 * (misma regla que E1.3).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h2400dp-xhdpi")
class CompoundSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun locked() = check("locked") { CompoundGalleries.Locked() }

    @Test fun chat() = check("chat") { CompoundGalleries.Chat() }

    @Test fun banners() = check("banners") { CompoundGalleries.Banners() }

    @Test fun states() = check("states") { CompoundGalleries.States() }

    @Test fun rows() = check("rows") { CompoundGalleries.Rows() }

    @Test fun footers() = check("footers") { CompoundGalleries.Footers() }

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
                widthDp >= 47.99f && heightDp >= 47.99f
            )
            val editable = node.config.getOrNull(SemanticsProperties.EditableText) != null
            assertTrue(
                "$id no declara rol",
                node.config.getOrNull(SemanticsProperties.Role) != null || editable
            )
            val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
            val description = node.config.getOrNull(
                SemanticsProperties.ContentDescription
            ).orEmpty()
            assertTrue(
                "$id no tiene texto ni contentDescription",
                texts.any { it.text.isNotBlank() } || description.any { it.isNotBlank() } ||
                    editable
            )
        }
    }
}
