package me.santiagoreina.parcheu.designsystem.graph.layout

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadialLayoutTest {
    private val modes = listOf("ESTUDIO", "DEPORTE", "AMISTAD", "PAREJA")

    private fun sample(n: Int, affinity: (Int) -> Int = { 95 - (it * 7) % 40 }) =
        (0 until n).map { LayoutNode("u$it", modes[it % modes.size], affinity(it)) }

    @Test
    fun `la misma entrada produce exactamente la misma salida`() {
        val input = sample(30)
        val fof = listOf(FofNode("f1"), FofNode("f2", "ESTUDIO"))
        val a = RadialLayout.compute(input, 328f, fof)
        val b = RadialLayout.compute(input.reversed(), 328f, fof.reversed())
        assertEquals(a, b)
    }

    @Test
    fun `afinidad 85 cae en el anillo 1, 84 en el 2 y 69 en el 3`() {
        val l = RadialLayout.compute(
            listOf(
                LayoutNode("a", "X", 85),
                LayoutNode("b", "X", 84),
                LayoutNode("c", "X", 69),
            ),
            328f,
        )
        val ring = l.nodes.associate { it.id to it.ring }
        assertEquals(1, ring["a"])
        assertEquals(2, ring["b"])
        assertEquals(3, ring["c"])
        val dist = l.nodes.associate { it.id to hypot(it.x - l.centerX, it.y - l.centerY) }
        assertEquals(62f, dist["a"]!!, 0.5f)
        assertEquals(102f, dist["b"]!!, 0.5f)
        assertEquals(140f, dist["c"]!!, 0.5f)
    }

    @Test
    fun `con fof los anillos pasan a 48 80 108 y la capa fof a 140`() {
        val l = RadialLayout.compute(sample(3, { 90 }), 328f, listOf(FofNode("f")))
        assertEquals(listOf(48f, 80f, 108f, 140f), l.ringRadii)
    }

    private fun assertSeparated(l: GraphLayout) {
        val all = l.nodes + l.fof
        for (i in all.indices) {
            for (j in i + 1 until all.size) {
                val d = hypot(all[i].x - all[j].x, all[i].y - all[j].y)
                val need = all[i].radius + all[j].radius + 2f
                assertTrue("${all[i].id}-${all[j].id} d=$d", d >= need)
            }
        }
    }

    @Test
    fun `con 30 nodos ningun par queda a menos de 2r + 2 dp`() {
        assertSeparated(RadialLayout.compute(sample(30), 328f))
        assertSeparated(RadialLayout.compute(sample(30) { 40 }, 328f))
        val fof = (0 until 6).map { FofNode("f$it") }
        assertSeparated(RadialLayout.compute(sample(30), 328f, fof))
    }

    @Test
    fun `hitTest devuelve el nodo mas cercano dentro de su radio y null fuera`() {
        val l = RadialLayout.compute(sample(8), 328f)
        val n = l.nodes.first()
        assertEquals(n.id, l.hitTest(n.x + n.radius * 0.5f, n.y)?.id)
        assertNotNull(l.hitTest(n.x, n.y))
        assertNull(l.hitTest(n.x + n.radius + 0.5f, n.y).takeIf { it?.id == n.id })
        assertNull(l.hitTest(0f, 0f))
    }

    private val ringsNoFof = listOf(62f, 102f, 140f)

    private fun assertInBand(l: GraphLayout, radii: List<Float>) {
        val k = l.size / RadialLayout.BASE_SIZE
        for (n in l.nodes) {
            val base = radii[n.ring - 1] * k
            val d = hypot(n.x - l.centerX, n.y - l.centerY)
            val prev = if (n.ring == 1) 0f else radii[n.ring - 2]
            val next = if (n.ring == 3) 2 * l.size else radii[n.ring]
            val lo = (prev + radii[n.ring - 1]) / 2f * k
            val hi = (radii[n.ring - 1] + next) / 2f * k
            val msg = "${n.id} ring=${n.ring} d=$d base=$base"
            assertTrue(msg, d >= lo - 0.01f && d <= hi + 0.01f)
        }
    }

    @Test
    fun `un anillo lleno mantiene los nodos dentro de su banda sin solapes`() {
        val l = RadialLayout.compute(sample(30) { 95 }, 328f)
        assertTrue(l.nodes.all { it.ring == 1 })
        assertInBand(l, ringsNoFof)
        assertSeparated(l)
        assertEquals(false, l.hasOverlaps)
    }

    @Test
    fun `mas de 11 nodos en el anillo 1 con fof tambien respetan su banda`() {
        val fof = (0 until 6).map { FofNode("f$it") }
        val l = RadialLayout.compute(sample(14) { 90 }, 328f, fof)
        assertInBand(l, listOf(48f, 80f, 108f))
        assertSeparated(l)
    }

    @Test
    fun `anillo lleno en un lienzo distinto sigue siendo determinista y separado`() {
        val input = sample(40) { 88 }
        val a = RadialLayout.compute(input, 200f)
        assertEquals(a, RadialLayout.compute(input.reversed(), 200f))
        assertSeparated(a)
    }

    @Test
    fun `si no caben ni con radio minimo avisa con hasOverlaps`() {
        val l = RadialLayout.compute(sample(400) { 95 }, 328f)
        assertTrue(l.hasOverlaps)
    }

    @Test(timeout = 5000)
    fun `tamano no finito o no positivo devuelve layout vacio sin colgarse`() {
        val one = listOf(LayoutNode("a", "A", 90))
        for (bad in listOf(Float.NaN, 0f, -5f, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val l = RadialLayout.compute(one, bad)
            assertTrue(l.nodes.isEmpty() && l.fof.isEmpty())
            assertEquals(false, l.hasOverlaps)
        }
    }

    @Test(timeout = 20000)
    fun `29 conexiones en el anillo 1 mas fof a 328 quedan separadas`() {
        val fof = (0 until 10).map { FofNode("f$it") }
        val l = RadialLayout.compute(sample(29) { 92 }, 328f, fof)
        assertSeparated(l)
        assertEquals(false, l.hasOverlaps)
    }

    @Test(timeout = 20000)
    fun `15 y 20 nodos en la miniatura de 160 quedan separados`() {
        for (n in listOf(15, 20)) {
            val l = RadialLayout.compute(sample(n), 160f)
            assertSeparated(l)
            assertEquals(false, l.hasOverlaps)
        }
    }
}
