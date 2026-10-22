package me.santiagoreina.parcheu.designsystem.graph.layout

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Nodo de entrada del layout. [mode] es el nombre del modo (ordena el ángulo) y [affinity]
 * va de 0 a 100.
 */
data class LayoutNode(val id: String, val mode: String, val affinity: Int)

/** Amigo de amigo: se ubica siempre en la capa exterior. */
data class FofNode(val id: String, val mode: String = "")

enum class NodeKind { CONNECTION, FOF }

/** Nodo ya ubicado. Coordenadas y radio en dp dentro de un lienzo cuadrado de lado `size`. */
data class PlacedNode(val id: String, val kind: NodeKind, val ring: Int, val x: Float, val y: Float, val radius: Float)

data class GraphLayout(
    val size: Float,
    val centerX: Float,
    val centerY: Float,
    val centerRadius: Float,
    val ringRadii: List<Float>,
    val nodes: List<PlacedNode>,
    val fof: List<PlacedNode>,
    /** true si, aun con radios reducidos, algún par queda más cerca que la separación mínima. */
    val hasOverlaps: Boolean = false,
) {
    /**
     * Devuelve el nodo más cercano cuyo círculo contiene el punto (x, y), o null si el punto
     * cae fuera de todos. [slop] amplía el radio (zona táctil) en dp.
     */
    fun hitTest(x: Float, y: Float, slop: Float = 0f): PlacedNode? = (nodes + fof)
        .map { it to hypot(it.x - x, it.y - y) }
        .filter { (n, d) -> d <= n.radius + slop }
        .minWithOrNull(compareBy({ it.second }, { it.first.id }))
        ?.first
}

/**
 * Layout radial determinista del grafo G0 (ADR-0007). Función pura, sin dependencias de Compose.
 *
 * Cada nodo permanece siempre dentro de la banda de su anillo (entre los puntos medios con los
 * anillos vecinos), de modo que el anillo dibujado refleja la afinidad. Si un anillo se llena, se
 * usan varias filas dentro de la banda y, si aun así no caben, se reduce el radio de sus nodos
 * hasta [MIN_NODE_R]. Si ni así caben, [GraphLayout.hasOverlaps] queda en true.
 */
object RadialLayout {
    const val BASE_SIZE = 328f
    const val MIN_GAP = 2f
    private const val CENTER_R = 25f
    private val RINGS = listOf(62f, 102f, 140f)
    private val RINGS_WITH_FOF = listOf(48f, 80f, 108f)
    private const val FOF_RING = 140f
    private val NODE_R = listOf(18f, 16f, 14f)
    private const val FOF_R = 13f
    private const val MIN_NODE_R = 5f
    private const val SHRINK = 0.92f
    private const val ROW_PACKING = 0.87f
    private const val ITERATIONS = 1000
    private const val MAX_ATTEMPTS = 40
    private const val MAX_SHRINK_STEPS = 60
    private const val MAX_NODE_R = 18f

    /** Anillo (1..3) según afinidad: >=85, 70-84, <70. */
    fun ringOf(affinity: Int): Int = when {
        affinity >= 85 -> 1
        affinity >= 70 -> 2
        else -> 3
    }

    fun compute(nodes: List<LayoutNode>, size: Float, fof: List<FofNode> = emptyList()): GraphLayout {
        if (!size.isFinite() || size <= 0f) {
            return GraphLayout(0f, 0f, 0f, 0f, emptyList(), emptyList(), emptyList())
        }
        val k = size / BASE_SIZE
        val c = size / 2f
        val centerR = CENTER_R * k
        val radii = (if (fof.isEmpty()) RINGS else RINGS_WITH_FOF).map { it * k }
        val allRadii = if (fof.isEmpty()) radii else radii + FOF_RING * k
        val sorted = nodes.sortedWith(compareBy({ it.mode }, { it.id }))
        val fofSorted = fof.sortedWith(compareBy({ it.mode }, { it.id }))

        // Si tras ubicar y relajar quedan pares solapados con el radio aun por encima del
        // mínimo, se repite todo con radios de partida menores.
        var scale = 1f
        var items: List<Item>
        var overlapping: Boolean
        var attempt = 0
        while (true) {
            items = placeAll(sorted, fofSorted, scale, allRadii, centerR, c, k)
            relax(items, c)
            overlapping = overlaps(items)
            val atMin = items.all { it.r <= min(it.fullR, MIN_NODE_R * k) + 1e-3f }
            attempt++
            if (!overlapping || attempt >= MAX_ATTEMPTS || scale * MAX_NODE_R <= MIN_NODE_R ||
                atMin
            ) {
                break
            }
            scale *= SHRINK
        }

        fun place(kind: NodeKind) = items.filter {
            it.kind == kind
        }.map { PlacedNode(it.id, kind, it.ring, it.x, it.y, it.r) }
        return GraphLayout(
            size,
            c,
            c,
            centerR,
            radii + FOF_RING * k,
            place(NodeKind.CONNECTION),
            place(NodeKind.FOF),
            hasOverlaps = overlapping,
        )
    }

    private fun placeAll(
        sorted: List<LayoutNode>,
        fofSorted: List<FofNode>,
        scale: Float,
        allRadii: List<Float>,
        centerR: Float,
        c: Float,
        k: Float,
    ): List<Item> {
        val items = ArrayList<Item>()
        for (ring in 1..3) {
            val ids = sorted.filter { ringOf(it.affinity) == ring }.map { it.id }
            items += placeRing(
                ids,
                NodeKind.CONNECTION,
                ring,
                NODE_R[ring - 1] * k * scale,
                allRadii,
                centerR,
                c,
                k,
            )
        }
        if (fofSorted.isNotEmpty()) {
            items += placeRing(
                fofSorted.map { it.id },
                NodeKind.FOF,
                4,
                FOF_R * k * scale,
                allRadii,
                centerR,
                c,
                k,
            )
        }
        items.forEach {
            it.x = c + (it.baseRadius * cos(it.angle)).toFloat()
            it.y = c + (it.baseRadius * sin(it.angle)).toFloat()
        }
        return items
    }

    // Ángulo estable: reparto uniforme por orden (modo, id) con desfase fijo por anillo.
    private fun angle(index: Int, count: Int, ring: Int): Double = -PI / 2 + 2 * PI * index / count + ring * 0.6

    private fun overlaps(items: List<Item>): Boolean {
        for (a in items.indices) {
            for (b in a + 1 until items.size) {
                val p = items[a]
                val q = items[b]
                if (hypot(q.x - p.x, q.y - p.y) < p.r + q.r + MIN_GAP - 0.01f) return true
            }
        }
        return false
    }

    private class Item(
        val id: String,
        val kind: NodeKind,
        val ring: Int,
        val baseRadius: Float,
        val angle: Double,
        val r: Float,
        val fullR: Float,
        val lo: Float,
        val hi: Float,
    ) {
        var x = 0f
        var y = 0f
    }

    /** Rango de radios (centro del nodo) permitido para el anillo [ring] con nodos de radio [r]. */
    private fun band(ring: Int, r: Float, allRadii: List<Float>, centerR: Float, c: Float): Pair<Float, Float> {
        val base = allRadii[ring - 1]
        var lo = centerR + r + MIN_GAP
        var hi = c - r
        if (ring > 1) lo = max(lo, min(base, (allRadii[ring - 2] + base) / 2f + r + 1f))
        if (ring < allRadii.size) hi = min(hi, max(base, (base + allRadii[ring]) / 2f - r - 1f))
        lo = min(lo, base)
        hi = max(hi, base)
        return lo to min(hi, max(lo, c - r))
    }

    private fun rowsOf(lo: Float, hi: Float, base: Float, r: Float, count: Int): List<Float> {
        val pitch = 2 * r + MIN_GAP
        val maxRows = (floor((hi - lo) / (ROW_PACKING * pitch)).toInt() + 1).coerceAtLeast(1)
        for (m in 1..maxRows) {
            val rows = if (m ==
                1
            ) {
                listOf(base.coerceIn(lo, hi))
            } else {
                List(m) { lo + (hi - lo) * it / (m - 1) }
            }
            if (rows.sumOf { floor(2 * PI * it / pitch).toInt() } >= count) return rows
        }
        return List(maxRows) {
            if (maxRows ==
                1
            ) {
                base.coerceIn(lo, hi)
            } else {
                lo + (hi - lo) * it / (maxRows - 1)
            }
        }
    }

    // Ubica los nodos de un anillo: reduce el radio hasta que quepan y los reparte en filas
    // dentro de la banda.
    private fun placeRing(
        ids: List<String>,
        kind: NodeKind,
        ring: Int,
        fullR: Float,
        allRadii: List<Float>,
        centerR: Float,
        c: Float,
        k: Float,
    ): List<Item> {
        if (ids.isEmpty()) return emptyList()
        val base = allRadii[ring - 1]
        val minR = min(fullR, MIN_NODE_R * k)
        var r = fullR
        var rows: List<Float>
        var steps = 0
        while (true) {
            val (lo, hi) = band(ring, r, allRadii, centerR, c)
            rows = rowsOf(lo, hi, base, r, ids.size)
            val pitch = 2 * r + MIN_GAP
            if (rows.sumOf { floor(2 * PI * it / pitch).toInt() } >= ids.size || r <= minR ||
                ++steps >= MAX_SHRINK_STEPS
            ) {
                break
            }
            r = max(minR, r * SHRINK)
        }
        val (lo, hi) = band(ring, r, allRadii, centerR, c)
        val pitch = 2 * r + MIN_GAP
        val caps = rows.map { max(1, floor(2 * PI * it / pitch).toInt()) }
        val total = caps.sum()
        // Reparto proporcional a la capacidad de cada fila, en orden estable.
        val counts = IntArray(rows.size) { ids.size * caps[it] / total }
        var left = ids.size - counts.sum()
        var j = 0
        while (left > 0) {
            counts[j % rows.size]++
            left--
            j++
        }
        val out = ArrayList<Item>()
        var idx = 0
        rows.forEachIndexed { row, rho ->
            for (i in 0 until counts[row]) {
                val a = angle(i, counts[row], ring) + (row % 2) * PI / counts[row].coerceAtLeast(1)
                out += Item(ids[idx++], kind, ring, rho, a, r, fullR, lo, hi)
            }
        }
        return out
    }

    // Relajación determinista: empuja pares solapados y mantiene un resorte suave hacia su fila.
    private fun relax(items: List<Item>, c: Float) {
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
                clamp(it, c)
            }
        }
    }

    // Mantiene cada nodo dentro de la banda de su anillo (fuera del centro y dentro del lienzo).
    private fun clamp(it: Item, c: Float) {
        val dx = it.x - c
        val dy = it.y - c
        val d = max(hypot(dx, dy), 1e-4f)
        val t = min(max(d, it.lo), it.hi)
        if (t != d) {
            it.x = c + dx / d * t
            it.y = c + dy / d * t
        }
    }
}
