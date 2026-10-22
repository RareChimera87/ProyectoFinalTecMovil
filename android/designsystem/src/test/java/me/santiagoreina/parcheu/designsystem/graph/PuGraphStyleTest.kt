package me.santiagoreina.parcheu.designsystem.graph

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import me.santiagoreina.parcheu.designsystem.theme.DarkParcheUColors
import me.santiagoreina.parcheu.designsystem.theme.LightParcheUColors
import me.santiagoreina.parcheu.designsystem.theme.Wcag
import me.santiagoreina.parcheu.designsystem.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas de estilo de G0 (inventario §3.7) sin Compose. */
class PuGraphStyleTest {
    private val laura = PuGraphSamples.nodes.single { it.id == "lau" }
    private val mateo = PuGraphSamples.nodes.single { it.id == "mat" }

    @Test
    fun `sin filtro ni seleccion nada se atenua`() {
        PuGraphSamples.nodes.forEach { assertFalse(isDimmed(it, null, null, emptySet())) }
    }

    @Test
    fun `el filtro de modo atenua los otros modos`() {
        assertFalse(isDimmed(laura, GraphMode.ESTUDIO, null, emptySet()))
        assertTrue(isDimmed(mateo, GraphMode.ESTUDIO, null, emptySet()))
    }

    @Test
    fun `seleccionar un nodo atenua el resto`() {
        assertFalse(isDimmed(laura, null, "lau", emptySet()))
        assertTrue(isDimmed(mateo, null, "lau", emptySet()))
    }

    @Test
    fun `dimmedIds atenua nodos sueltos`() {
        assertTrue(isDimmed(laura, null, null, setOf("lau")))
    }

    @Test
    fun `opacidades del inventario`() {
        assertEquals(0.95f, edgeAlpha(laura, dimmed = false))
        assertEquals(0.3f, edgeAlpha(mateo, dimmed = false))
        assertEquals(0.1f, edgeAlpha(laura, dimmed = true))
        assertEquals(1f, nodeAlpha(laura, dimmed = false))
        assertEquals(0.7f, nodeAlpha(mateo, dimmed = false))
        assertEquals(0.22f, nodeAlpha(mateo, dimmed = true))
    }

    @Test
    fun `los amigos de amigos quedan en la capa exterior y comprimen los anillos`() {
        val layout = computeGraphLayout(PuGraphSamples.nodes, PuGraphSamples.fof, 328f)
        assertEquals(PuGraphSamples.fof.size, layout.fof.size)
        assertEquals(listOf(48f, 80f, 108f, 140f), layout.ringRadii)
    }

    @Test
    fun `cada amigo de amigo queda cerca del angulo de su conexion de origen`() {
        val layout = computeGraphLayout(PuGraphSamples.nodes, PuGraphSamples.fof, 328f)
        val c = layout.centerX
        fun angle(x: Float, y: Float) = atan2((y - c).toDouble(), (x - c).toDouble())
        val failures = PuGraphSamples.fof.mapNotNull { f ->
            val parent = layout.nodes.single { it.id == f.parentId!! }
            val placed = layout.fof.single { it.id == f.id }
            var diff = abs(angle(placed.x, placed.y) - angle(parent.x, parent.y))
            if (diff > PI) diff = 2 * PI - diff
            if (diff > Math.toRadians(60.0)) "${f.id}: ${Math.toDegrees(diff)} grados" else null
        }
        assertTrue("fof lejos de su padre: $failures", failures.isEmpty())
        // Siguen en la capa exterior.
        layout.fof.forEach { assertEquals(140f, hypot(it.x - c, it.y - c), 0.01f) }
    }

    @Test
    fun `los amigos de amigos no se tocan entre si`() {
        val layout = computeGraphLayout(PuGraphSamples.nodes, PuGraphSamples.fof, 328f)
        for (a in layout.fof) {
            for (b in layout.fof) {
                if (a.id <
                    b.id
                ) {
                    assertTrue(
                        "${a.id}/${b.id}",
                        hypot(a.x - b.x, a.y - b.y) >= a.radius + b.radius,
                    )
                }
            }
        }
    }

    @Test
    fun `hermanos comparten padre y se abren en abanico`() {
        val layout = computeGraphLayout(PuGraphSamples.nodes, PuGraphSamples.fof, 328f)
        val f1 = layout.fof.single { it.id == "f1" }
        val f2 = layout.fof.single { it.id == "f2" }
        assertTrue(hypot(f1.x - f2.x, f1.y - f2.y) >= 2 * f1.radius)
    }

    @Test
    fun `el texto de un nodo pendiente conserva opacidad plena y el atenuado no`() {
        assertEquals(1f, textAlpha(dimmed = false))
        assertEquals(0.22f, textAlpha(dimmed = true))
    }

    @Test
    fun `iniciales y etiqueta de nodos pendientes cumplen AA en ambos temas`() {
        val failures = mutableListOf<String>()
        for ((theme, colors) in listOf(
            "oscuro" to DarkParcheUColors,
            "claro" to LightParcheUColors,
        )) {
            val label = contrastRatio(colors.mu, colors.bg)
            if (label < Wcag.AA_NORMAL_TEXT) failures += "$theme etiqueta: $label"
            for (affinity in 50..100) {
                val node =
                    GraphNode("x", "Ana", "A", affinity, GraphMode.ESTUDIO, GraphEdgeState.PENDING)
                val avatar = nodeAvatarColors(node, colors.isDark)
                // Relleno compuesto con el 0,7 de PENDING sobre el fondo, como lo pinta el Canvas.
                val composed = avatar.fill.copy(alpha = avatar.fill.alpha * GraphAlpha.NODE_PENDING)
                val ratio = contrastRatio(avatar.content, composed, backdrop = colors.bg)
                if (ratio < Wcag.AA_NORMAL_TEXT) failures += "$theme iniciales $affinity: $ratio"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `un amigo de amigo sin padre conserva su posicion radial y no falla`() {
        val fof = listOf(GraphFof("f1", null, "", "", 80), GraphFof("f2", "no-existe", "", "", 75))
        val layout = computeGraphLayout(PuGraphSamples.nodes, fof, 300f)
        assertEquals(2, layout.fof.size)
    }
}
