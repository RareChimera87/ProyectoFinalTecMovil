package me.santiagoreina.parcheu.designsystem.graph

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
}
