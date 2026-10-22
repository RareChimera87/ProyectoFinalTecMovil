package me.santiagoreina.parcheu.designsystem.graph.layout

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Nodo de entrada del layout. [mode] es el nombre del modo (ordena el ángulo) y [affinity] va de 0 a 100. */
data class LayoutNode(val id: String, val mode: String, val affinity: Int)

/** Amigo de amigo: se ubica siempre en la capa exterior. */
data class FofNode(val id: String, val mode: String = "")

enum class NodeKind { CONNECTION, FOF }

/** Nodo ya ubicado. Coordenadas y radio en dp dentro de un lienzo cuadrado de lado `size`. */
data class PlacedNode(
    val id: String,
    val kind: NodeKind,
    val ring: Int,
    val x: Float,
    val y: Float,
    val radius: Float,
)

data class GraphLayout(
    val size: Float,
    val centerX: Float,
    val centerY: Float,
    val centerRadius: Float,
    val ringRadii: List<Float>,
    val nodes: List<PlacedNode>,
    val fof: List<PlacedNode>,
) {
    /**
     * Devuelve el nodo más cercano cuyo círculo contiene el punto (x, y), o null si el punto
     * cae fuera de todos. [slop] amplía el radio (zona táctil) en dp.
     */
    fun hitTest(x: Float, y: Float, slop: Float = 0f): PlacedNode? =
        (nodes + fof)
            .map { it to hypot(it.x - x, it.y - y) }
            .filter { (n, d) -> d <= n.radius + slop }
            .minWithOrNull(compareBy({ it.second }, { it.first.id }))
            ?.first
}

/** Layout radial determinista del grafo G0 (ADR-0007). Función pura, sin dependencias de Compose. */
object RadialLayout {
    const val BASE_SIZE = 328f
    const val MIN_GAP = 2f
    private const val CENTER_R = 25f
    private val RINGS = listOf(62f, 102f, 140f)
    private val RINGS_WITH_FOF = listOf(48f, 80f, 108f)
    private const val FOF_RING = 140f
    private val NODE_R = listOf(18f, 16f, 14f)
    private const val FOF_R = 13f
    private const val ITERATIONS = 1000

    /** Anillo (1..3) según afinidad: >=85, 70-84, <70. */
    fun ringOf(affinity: Int): Int =
        when {
            affinity >= 85 -> 1
            affinity >= 70 -> 2
            else -> 3
        }

    fun compute(
        nodes: List<LayoutNode>,
        size: Float,
        fof: List<FofNode> = emptyList(),
    ): GraphLayout {
        val k = size / BASE_SIZE
        val c = size / 2f
        val radii = (if (fof.isEmpty()) RINGS else RINGS_WITH_FOF).map { it * k }
        val sorted = nodes.sortedWith(compareBy({ it.mode }, { it.id }))

        val items = ArrayList<Item>()
        for (ring in 1..3) {
            val group = sorted.filter { ringOf(it.affinity) == ring }
            val r = NODE_R[ring - 1] * k
            group.forEachIndexed { i, n ->
                items += Item(n.id, NodeKind.CONNECTION, ring, radii[ring - 1], angle(i, group.size, ring), r)
            }
        }
        if (fof.isNotEmpty()) {
            val group = fof.sortedWith(compareBy({ it.mode }, { it.id }))
            group.forEachIndexed { i, n ->
                items += Item(n.id, NodeKind.FOF, 4, FOF_RING * k, angle(i, group.size, 4), FOF_R * k)
            }
        }
        items.forEach {
            it.x = c + (it.baseRadius * cos(it.angle)).toFloat()
            it.y = c + (it.baseRadius * sin(it.angle)).toFloat()
        }
        relax(items, c, size, CENTER_R * k)

        fun place(kind: NodeKind) =
            items.filter { it.kind == kind }.map { PlacedNode(it.id, kind, it.ring, it.x, it.y, it.r) }
        return GraphLayout(size, c, c, CENTER_R * k, radii + FOF_RING * k, place(NodeKind.CONNECTION), place(NodeKind.FOF))
    }

    // Ángulo estable: reparto uniforme por orden (modo, id) con desfase fijo por anillo.
    private fun angle(index: Int, count: Int, ring: Int): Double =
        -PI / 2 + 2 * PI * index / count + ring * 0.6

    private class Item(
        val id: String,
        val kind: NodeKind,
        val ring: Int,
        val baseRadius: Float,
        val angle: Double,
        val r: Float,
    ) {
        var x = 0f
        var y = 0f
    }

    // Relajación determinista: empuja pares solapados y mantiene un resorte suave hacia su anillo.
    private fun relax(items: List<Item>, c: Float, size: Float, centerR: Float) {
        for (iter in 0 until ITERATIONS) {
            val spring = if (iter < ITERATIONS / 2) 0.08f else 0f
            for (a in items.indices) {
                for (b in a + 1 until items.size) {
                    val p = items[a]
                    val q = items[b]
                    val need = p.r + q.r + MIN_GAP + 0.05f
                    var dx = q.x - p.x
                    var dy = q.y - p.y
                    var d = hypot(dx, dy)
                    if (d >= need) continue
                    if (d < 1e-4f) {
                        dx = cos(p.angle).toFloat()
                        dy = sin(p.angle).toFloat()
                        d = 1f
                    }
                    val push = (need - d) / 2f
                    val ux = dx / d
                    val uy = dy / d
                    p.x -= ux * push
                    p.y -= uy * push
                    q.x += ux * push
                    q.y += uy * push
                }
            }
            for (it in items) {
                if (spring > 0f) {
                    val dx = it.x - c
                    val dy = it.y - c
                    val d = max(hypot(dx, dy), 1e-4f)
                    val pull = (it.baseRadius - d) * spring
                    it.x += dx / d * pull
                    it.y += dy / d * pull
                }
                clamp(it, c, size, centerR)
            }
        }
    }

    // Mantiene el nodo dentro del lienzo y fuera del nodo central.
    private fun clamp(it: Item, c: Float, size: Float, centerR: Float) {
        val dx = it.x - c
        val dy = it.y - c
        val d = max(hypot(dx, dy), 1e-4f)
        val lo = centerR + it.r + MIN_GAP
        val hi = size / 2f - it.r
        val t = min(max(d, lo), max(lo, hi))
        if (t != d) {
            it.x = c + dx / d * t
            it.y = c + dy / d * t
        }
    }
}
