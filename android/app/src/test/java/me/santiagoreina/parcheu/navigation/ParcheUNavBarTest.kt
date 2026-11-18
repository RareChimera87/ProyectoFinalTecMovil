package me.santiagoreina.parcheu.navigation

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** N0 con iconos del sistema de diseño: cada raíz tiene icono, la activa usa la variante rellena. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class ParcheUNavBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val resources =
        ApplicationProvider.getApplicationContext<android.content.Context>().resources

    @Test
    fun cadaRaizTieneIconoYVarianteRellena() {
        val expected = mapOf(
            TopLevelDestination.HOME to "ic_home",
            TopLevelDestination.PARCHE to "ic_hub",
            TopLevelDestination.CONOCER to "ic_shuffle",
            TopLevelDestination.CHATS to "ic_chat_bubble",
            TopLevelDestination.PERFIL to "ic_person"
        )
        assertEquals(TopLevelDestination.entries.toSet(), expected.keys)
        TopLevelDestination.entries.forEach { destination ->
            assertNotEquals(destination.name, destination.icon, destination.activeIcon)
            // getResourceEntryName solo lee el nombre en la prueba; el código usa referencias directas a R.
            val icon = resources.getResourceEntryName(destination.icon)
            val active = resources.getResourceEntryName(destination.activeIcon)
            assertEquals(destination.name, expected.getValue(destination), icon)
            assertEquals(destination.name, "${icon}_filled", active)
            assertTrue(resources.getResourceTypeName(destination.icon) == "drawable")
        }
    }

    @Test
    fun soloLaPestanaActivaQuedaSeleccionadaYSeAnunciaComoPestana() {
        var selected: TopLevelDestination? = null
        composeRule.setContent {
            ParcheUTheme {
                ParcheUNavBar(active = TopLevelDestination.CONOCER, onSelect = { selected = it })
            }
        }
        TopLevelDestination.entries.forEach { destination ->
            val node = composeRule.onNodeWithTag(navBarTabTag(destination))
            if (destination ==
                TopLevelDestination.CONOCER
            ) {
                node.assertIsSelected()
            } else {
                node.assertIsNotSelected()
            }
            node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            node.assertTextEquals(destination.label)
            node.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
        composeRule.onNodeWithTag(navBarTabTag(TopLevelDestination.CHATS)).performClick()
        assertEquals(TopLevelDestination.CHATS, selected)
        val roles = TopLevelDestination.entries.mapNotNull {
            composeRule.onNodeWithTag(navBarTabTag(it)).fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.Role)
        }
        assertEquals(TopLevelDestination.entries.size, roles.size)
    }
}
