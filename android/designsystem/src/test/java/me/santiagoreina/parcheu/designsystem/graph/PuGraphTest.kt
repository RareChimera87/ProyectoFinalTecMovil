package me.santiagoreina.parcheu.designsystem.graph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Semántica (lista paralela para TalkBack) y toques de G0. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class PuGraphTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val clicks = mutableListOf<String>()

    private fun setGraph(fof: List<GraphFof> = emptyList()) {
        composeRule.setContent {
            ParcheUTheme(darkTheme = true) {
                PuGraph(nodes = PuGraphSamples.nodes, fof = fof, onNodeClick = { clicks += it })
            }
        }
    }

    @Test
    fun `cada nodo expone nombre, afinidad, modo y estado`() {
        setGraph()
        composeRule.onNodeWithContentDescription("Laura P., 87 % afín, Estudio, conectados")
            .assertExists()
        composeRule.onNodeWithContentDescription("Daniela, 54 % afín, Estudio, simularon")
            .assertExists()
        composeRule.onNodeWithContentDescription("Mateo, 58 % afín, Amistad, pendiente")
            .assertExists()
        // Anónimo: sin nombre.
        composeRule.onNodeWithContentDescription("Alguien, 50 % afín, Amistad, pendiente")
            .assertExists()
        composeRule.onAllNodesWithContentDescription("afín", substring = true)
            .assertCountEquals(PuGraphSamples.nodes.size)
    }

    @Test
    fun `la capa de amigos de amigos tambien se anuncia`() {
        setGraph(fof = PuGraphSamples.fof)
        composeRule.onNodeWithContentDescription(
            "Felipe R., 84 % afín estimada, amigo de amigo"
        ).assertExists()
        composeRule.onAllNodesWithContentDescription("Alguien, ", substring = true)
            .assertCountEquals(4) // Nico + 3 fof anónimos.
    }

    @Test
    fun `tocar las coordenadas de un nodo dispara onNodeClick con su id`() {
        setGraph()
        val layout = computeGraphLayout(PuGraphSamples.nodes, emptyList(), 328f)
        val density = composeRule.density.density
        for (target in listOf("lau", "val", "nico")) {
            val p = layout.nodes.single { it.id == target }
            composeRule.onNodeWithTag(PU_GRAPH_TEST_TAG).performTouchInput {
                click(Offset(p.x * density, p.y * density))
            }
        }
        assertEquals(listOf("lau", "val", "nico"), clicks)
    }

    @Test
    fun `tocar fuera de los nodos no dispara nada`() {
        setGraph()
        composeRule.onNodeWithTag(PU_GRAPH_TEST_TAG).performTouchInput {
            click(Offset(1f, 1f)) // Esquina: fuera de todo anillo.
        }
        composeRule.waitForIdle()
        assertTrue(clicks.isEmpty())
    }

    @Test
    fun `la accion de clic de TalkBack dispara onNodeClick`() {
        setGraph()
        composeRule.onNodeWithContentDescription("Laura P., 87 % afín, Estudio, conectados")
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("lau"), clicks)
    }

    @Test
    fun `tocar un amigo de amigo dispara onNodeClick con su id`() {
        setGraph(fof = PuGraphSamples.fof)
        val layout = computeGraphLayout(PuGraphSamples.nodes, PuGraphSamples.fof, 328f)
        val density = composeRule.density.density
        val p = layout.fof.single { it.id == "f3" }
        composeRule.onNodeWithTag(PU_GRAPH_TEST_TAG).performTouchInput {
            click(Offset(p.x * density, p.y * density))
        }
        assertEquals(listOf("f3"), clicks)
    }
}
