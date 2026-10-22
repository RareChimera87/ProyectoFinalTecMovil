package me.santiagoreina.parcheu.designsystem.components.compound

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-xhdpi")
class AvatarInitialsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun anonimoNoMuestraInicialesYSeDescribeComoAlguien() {
        composeRule.setContent {
            ParcheUTheme { AvatarInitials(name = "Camila Rojas", affinity = 90, anonymous = true) }
        }
        composeRule.onNodeWithContentDescription(
            "Alguien",
        ).assertContentDescriptionEquals("Alguien")
        // Ni el nombre ni sus iniciales aparecen en ninguna parte del árbol de semántica.
        val nodes = composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode().let {
            listOf(it) +
                it.children
        }
        nodes.forEach { node ->
            val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            assertTrue("El avatar anónimo no debe tener texto, tiene $texts", texts.isEmpty())
            val description = node.config.getOrNull(
                SemanticsProperties.ContentDescription,
            ).orEmpty()
            assertTrue(description.none { it.contains("Camila") || it.contains("CR") })
        }
    }

    @Test
    fun noAnonimoMuestraInicialesYSeDescribeConElNombre() {
        composeRule.setContent {
            ParcheUTheme { AvatarInitials(name = "Camila Rojas", affinity = 90) }
        }
        composeRule.onNode(hasContentDescription("Camila Rojas")).assertExists()
    }

    @Test
    fun inicialesSonDosLetrasEnMayuscula() {
        assertEquals("CR", initialsOf("camila rojas"))
        assertEquals("AM", initialsOf("  Ana   María  Díaz "))
        assertEquals("L", initialsOf("luis"))
        assertEquals("", initialsOf("   "))
    }
}
