package me.santiagoreina.parcheu.designsystem.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import me.santiagoreina.parcheu.designsystem.graph.layout.FofNode
import me.santiagoreina.parcheu.designsystem.graph.layout.GraphLayout
import me.santiagoreina.parcheu.designsystem.graph.layout.LayoutNode
import me.santiagoreina.parcheu.designsystem.graph.layout.PlacedNode
import me.santiagoreina.parcheu.designsystem.graph.layout.RadialLayout
import me.santiagoreina.parcheu.designsystem.theme.AvatarColors
import me.santiagoreina.parcheu.designsystem.theme.InterFontFamily
import me.santiagoreina.parcheu.designsystem.theme.ParcheUColors
import me.santiagoreina.parcheu.designsystem.theme.ParcheUPalette
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.designsystem.theme.SoraFontFamily
import me.santiagoreina.parcheu.designsystem.theme.affinityAlpha
import me.santiagoreina.parcheu.designsystem.theme.avatarColors

/** Modo de una conexión. Mismos nombres que `contract.Mode` para mapear con `valueOf(dto.mode.name)`. */
enum class GraphMode(val label: String) {
    AMISTAD("Amistad"),
    PAREJA("Pareja"),
    ESTUDIO("Estudio")
}

/** Estado de la arista. Mismos nombres que `contract.EdgeState`. */
enum class GraphEdgeState(val label: String) {
    CONNECTED("conectados"),
    SIMULATED("simularon"),
    PENDING("pendiente")
}

/**
 * Conexión directa del usuario. [name] es la etiqueta visible ("Laura P."); si [anonymous] es true
 * no se muestran nombre ni iniciales ("Alguien", "?"). [affinity] va de 0 a 100.
 */
@Immutable
data class GraphNode(
    val id: String,
    val name: String,
    val initials: String,
    val affinity: Int,
    val mode: GraphMode,
    val state: GraphEdgeState,
    val anonymous: Boolean = false
)

/**
 * Amigo de amigo (capa ParcheU+). [parentId] es la conexión directa a través de la cual se llega;
 * es nulo mientras el contrato (FofCandidate de E3.1) no lo entregue: el fof conserva la posición
 * del layout radial y no dibuja línea punteada de origen. [affinity] es la estimación de ADR-0023.
 */
@Immutable
data class GraphFof(
    val id: String,
    val parentId: String?,
    val name: String,
    val initials: String,
    val affinity: Int,
    val anonymous: Boolean = true
)

/** Tamaño por debajo del cual el grafo se dibuja como miniatura (sin etiquetas). */
val PU_GRAPH_MINI_THRESHOLD: Dp = 200.dp

/** Tamaño de diseño de G0 (k = size / 328). */
val PU_GRAPH_DEFAULT_SIZE: Dp = RadialLayout.BASE_SIZE.dp

/** Margen táctil extra alrededor de cada nodo, en dp (igual al halo de selección). */
internal const val HIT_SLOP_DP = 6f

/** Opacidades del inventario §3.7. */
internal object GraphAlpha {
    const val EDGE_SOLID = 0.95f
    const val EDGE_PENDING = 0.3f
    const val EDGE_DIMMED = 0.1f
    const val NODE_PENDING = 0.7f
    const val NODE_DIMMED = 0.22f
    const val FOF_EDGE = 0.8f
}

/** Texto de TalkBack de un nodo: "Laura P., 87 % afín, Estudio, conectados". */
fun graphNodeDescription(node: GraphNode): String {
    val who = if (node.anonymous) "Alguien" else node.name
    return "$who, ${node.affinity} % afín, ${node.mode.label}, ${node.state.label}"
}

/** Texto de TalkBack de un amigo de amigo: "Alguien, 87 % afín estimada, amigo de amigo". */
fun graphFofDescription(fof: GraphFof): String {
    val who = if (fof.anonymous) "Alguien" else fof.name
    return "$who, ${fof.affinity} % afín estimada, amigo de amigo"
}

/** true si el nodo se atenúa por selección de otro nodo, por filtro de modo o por [dimmedIds]. */
internal fun isDimmed(
    node: GraphNode,
    modeFilter: GraphMode?,
    selectedId: String?,
    dimmedIds: Set<String>
): Boolean = (selectedId != null && selectedId != node.id) ||
    (modeFilter != null && node.mode != modeFilter) ||
    node.id in dimmedIds

internal fun edgeAlpha(node: GraphNode, dimmed: Boolean): Float = when {
    dimmed -> GraphAlpha.EDGE_DIMMED
    node.state == GraphEdgeState.PENDING -> GraphAlpha.EDGE_PENDING
    else -> GraphAlpha.EDGE_SOLID
}

internal fun nodeAlpha(node: GraphNode, dimmed: Boolean): Float = when {
    dimmed -> GraphAlpha.NODE_DIMMED
    node.state == GraphEdgeState.PENDING -> GraphAlpha.NODE_PENDING
    else -> 1f
}

/**
 * Opacidad de las iniciales y la etiqueta. El 0,7 de PENDING es solo del relleno, el borde y la arista:
 * el texto conserva color pleno para cumplir AA (ADR-0030). Un nodo atenuado (0,22) es inactivo y se
 * atenúa entero, texto incluido (WCAG 1.4.3 exime los componentes inactivos).
 */
internal fun textAlpha(dimmed: Boolean): Float = if (dimmed) GraphAlpha.NODE_DIMMED else 1f

/**
 * Colores del avatar de un nodo. El relleno se pinta con la afinidad; pero el color de las iniciales
 * se elige con la opacidad efectiva del relleno (afinidad por el 0,7 de PENDING), porque el relleno
 * queda compuesto sobre el fondo del tema y el blanco pleno podría no llegar a AA (ADR-0030).
 */
internal fun nodeAvatarColors(node: GraphNode, isDark: Boolean): AvatarColors {
    val fillAlpha = affinityAlpha(node.affinity)
    val effective = if (node.state ==
        GraphEdgeState.PENDING
    ) {
        fillAlpha * GraphAlpha.NODE_PENDING
    } else {
        fillAlpha
    }
    val fill = avatarColors(fillAlpha, isDark).fill
    return avatarColors(effective, isDark).copy(fill = fill)
}

/**
 * Layout de G0 para un lienzo de [sizeDp] dp. Los amigos de amigos se ordenan por su conexión de
 * origen para que queden agrupados en la capa exterior. Coordenadas en dp.
 */
internal fun computeGraphLayout(
    nodes: List<GraphNode>,
    fof: List<GraphFof>,
    sizeDp: Float
): GraphLayout {
    val modeOf = nodes.associate { it.id to it.mode.name }
    val base = RadialLayout.compute(
        nodes = nodes.map { LayoutNode(it.id, it.mode.name, it.affinity) },
        size = sizeDp,
        fof = fof.map {
            FofNode(it.id, it.parentId?.let { p -> "${modeOf[p].orEmpty()}:$p" } ?: "")
        }
    )
    return if (base.fof.isEmpty()) base else base.copy(fof = alignFofToParents(base, nodes, fof))
}

/**
 * Separación mínima en dp entre los centros de dos amigos de amigos vecinos sobre la capa exterior.
 * Deja sitio a la etiqueta ("Alguien · 82 %", unos 68 dp de ancho) del vecino.
 */
private const val FOF_SPACING_DP = 62f

/**
 * Recoloca cada amigo de amigo en el ángulo de su conexión de origen (como en PU Grafo), con un abanico
 * entre hermanos, para que la línea punteada sea corta y no cruce el grafo. Conserva el radio de la
 * capa exterior que calculó [RadialLayout]. Si dos grupos vecinos quedarían tapándose, se separan
 * angularmente. Un fof sin padre conocido conserva la posición de [RadialLayout].
 */
private fun alignFofToParents(
    layout: GraphLayout,
    nodes: List<GraphNode>,
    fof: List<GraphFof>
): List<PlacedNode> {
    val parentOf = fof.mapNotNull { f -> f.parentId?.let { f.id to it } }.toMap()
    val placedById = layout.nodes.associateBy { it.id }
    val cx = layout.centerX
    val cy = layout.centerY
    fun angleOf(p: PlacedNode) = atan2(p.y - cy, p.x - cx)

    val anchored = mutableListOf<Pair<PlacedNode, Double>>()
    val loose = mutableListOf<PlacedNode>()
    layout.fof.sortedBy { it.id }.forEach { f ->
        val parent = parentOf[f.id]?.let { placedById[it] }
        if (parent == null) loose += f else anchored += f to angleOf(parent).toDouble()
    }
    if (anchored.isEmpty()) return layout.fof

    val outer = layout.ringRadii.last()
    // Paso angular mínimo entre centros vecinos sobre el arco exterior (escala con el lienzo).
    val step = FOF_SPACING_DP * (layout.size / RadialLayout.BASE_SIZE) / outer.toDouble()

    // Abanico entre hermanos centrado en el ángulo del padre.
    val targets = anchored.groupBy { it.second }.flatMap { (angle, group) ->
        group.mapIndexed { i, (f, _) -> f to angle + (i - (group.size - 1) / 2.0) * step }
    }.sortedBy { normalize(it.second) }

    // Separa vecinos que se pisan: barrido circular que empuja hacia delante hasta que quepan.
    val angles = targets.map { normalize(it.second) }.toDoubleArray()
    repeat(targets.size) {
        for (i in angles.indices) {
            val j = (i + 1) % angles.size
            if (i == j) continue
            val gap = normalize(angles[j] - angles[i])
            if (gap < step) {
                val push = (step - gap) / 2.0
                angles[i] -= push
                angles[j] += push
            }
        }
    }

    val k = layout.size / RadialLayout.BASE_SIZE
    // Cajas ocupadas por las conexiones (círculo + etiqueta) para que la capa exterior no las pise.
    val labelOf = nodes.associate { it.id to if (it.anonymous) "Alguien" else it.name }
    val obstacles = layout.nodes.map { n ->
        listOf(
            Box(n.x - n.radius, n.y - n.radius, n.x + n.radius, n.y + n.radius),
            labelBox(n, labelOf[n.id].orEmpty(), 13f * k, CONNECTION_CHAR_DP * k)
        )
    }.flatten()
    val fofLabelOf = fof.associate {
        it.id to "${if (it.anonymous) "Alguien" else it.name} · ${it.affinity} %"
    }

    fun position(angle: Double) = (cx + outer * cos(angle)).toFloat() to
        (cy + outer * sin(angle)).toFloat()

    fun collides(
        f: PlacedNode,
        x: Float,
        y: Float,
        others: List<Pair<Float, Float>>,
        extra: List<Box> = emptyList()
    ): Boolean {
        val moved = f.copy(x = x, y = y)
        val boxes = listOf(
            Box(x - f.radius, y - f.radius, x + f.radius, y + f.radius),
            labelBox(moved, fofLabelOf[f.id].orEmpty(), 12f * k, FOF_CHAR_DP * k)
        )
        if (boxes.any { b -> (obstacles + extra).any { it.intersects(b) } }) return true
        return others.any { (ox, oy) ->
            hypot(ox - x, oy - y) < 2f * f.radius + FOF_GROUP_GAP_DP * k
        }
    }

    // Desplaza cada grupo de hermanos como bloque, en pasos pequeños, hasta que ninguno pise a una
    // conexión ni a un vecino de otro grupo. Si no hay hueco dentro del rango se conserva el abanico.
    val finalPos = arrayOfNulls<Pair<Float, Float>>(targets.size)
    val shifts = (0..FOF_MAX_SHIFT_STEPS).flatMap { n ->
        if (n == 0) listOf(0.0) else listOf(n * FOF_SHIFT_STEP, -n * FOF_SHIFT_STEP)
    }
    val wideShifts = (0..FOF_MAX_SHIFT_STEPS * 2).flatMap { n ->
        if (n == 0) listOf(0.0) else listOf(n * FOF_SHIFT_STEP, -n * FOF_SHIFT_STEP)
    }
    val groups = targets.indices.groupBy { parentOf[targets[it].first.id] }.values
        .sortedBy { idx -> idx.minOf { angles[it] } }
    groups.forEach { idx ->
        val elsewhere = targets.indices.filter { it !in idx }.map { j ->
            finalPos[j] ?: position(angles[j])
        }
        val chosen = shifts.firstOrNull { shift ->
            idx.all { i ->
                val (x, y) = position(angles[i] + shift)
                !collides(targets[i].first, x, y, elsewhere)
            }
        }
        if (chosen != null) {
            idx.forEach { i -> finalPos[i] = position(angles[i] + chosen) }
        } else {
            // Ningún desplazamiento del bloque cabe: cada hermano busca su hueco por separado,
            // esquivando también a los hermanos ya colocados (círculo y etiqueta).
            fun boxesAt(i: Int, x: Float, y: Float): List<Box> {
                val f = targets[i].first
                return listOf(
                    Box(x - f.radius, y - f.radius, x + f.radius, y + f.radius),
                    labelBox(
                        f.copy(x = x, y = y),
                        fofLabelOf[f.id].orEmpty(),
                        12f * k,
                        FOF_CHAR_DP * k
                    )
                )
            }
            val boxesOf = idx.associateWith { i ->
                position(angles[i]).let { (x, y) -> boxesAt(i, x, y) }
            }.toMutableMap()
            idx.forEach { i ->
                val f = targets[i].first
                val mine = boxesOf.filterKeys { it != i }.values.flatten()
                val pick = wideShifts.map { angles[i] + it }.firstOrNull { a ->
                    val (x, y) = position(a)
                    !collides(f, x, y, elsewhere, mine)
                } ?: angles[i]
                val (x, y) = position(pick)
                finalPos[i] = x to y
                boxesOf[i] = boxesAt(i, x, y)
            }
        }
    }
    val placed = targets.mapIndexed { i, (f, _) ->
        val (x, y) = finalPos[i]!!
        f.copy(x = x, y = y)
    }
    return placed + loose
}

private const val CONNECTION_CHAR_DP = 5.6f
private const val FOF_CHAR_DP = 5.0f

/** Hueco mínimo en dp entre círculos de grupos distintos. */
private const val FOF_GROUP_GAP_DP = 8f
private const val FOF_SHIFT_STEP = 2 * PI / 180
private const val FOF_MAX_SHIFT_STEPS = 10

private data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
    fun intersects(o: Box) = l < o.r && o.l < r && t < o.b && o.t < b

    fun center() = (l + r) / 2f to (t + b) / 2f
}

/** Caja aproximada de la etiqueta centrada bajo el nodo [n], a [offset] dp por debajo del centro. */
private fun labelBox(n: PlacedNode, text: String, offset: Float, charDp: Float): Box {
    val half = text.length * charDp / 2f
    return Box(n.x - half, n.y + n.radius + offset - 9f, n.x + half, n.y + n.radius + offset + 3f)
}

private fun normalize(angle: Double): Double {
    val tau = 2 * PI
    return ((angle % tau) + tau) % tau
}

/**
 * Componente G0 (docs/arquitectura.md §2.6, ADR-0007): el usuario al centro y sus conexiones en
 * anillos por afinidad, dibujado con `Canvas`.
 *
 * - [modeFilter] atenúa los nodos de otros modos (null = todos); [dimmedIds] atenúa nodos sueltos
 *   (p. ej. filtro de comunidad en D1).
 * - [selectedId] dibuja el halo sobre ese nodo y atenúa el resto.
 * - [fof] activa la capa de amigos de amigos (los anillos se comprimen a 48/80/108 y la capa va en 140).
 * - Con [size] < 200 dp se dibuja la miniatura, sin etiquetas.
 * - Un toque sobre un nodo (o su margen de [HIT_SLOP_DP]) llama a [onNodeClick] con su id.
 * - TalkBack recorre una lista semántica paralela, un elemento por nodo, de mayor a menor afinidad.
 */
@Composable
fun PuGraph(
    nodes: List<GraphNode>,
    modifier: Modifier = Modifier,
    size: Dp = PU_GRAPH_DEFAULT_SIZE,
    modeFilter: GraphMode? = null,
    selectedId: String? = null,
    fof: List<GraphFof> = emptyList(),
    dimmedIds: Set<String> = emptySet(),
    centerLabel: String = "Tú",
    onNodeClick: (String) -> Unit = {}
) {
    val sizeDp = size.value
    val layout = remember(nodes, fof, sizeDp) { computeGraphLayout(nodes, fof, sizeDp) }
    val colors = ParcheUTheme.colors
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val currentOnClick = rememberUpdatedState(onNodeClick)
    val byId = remember(nodes) { nodes.associateBy { it.id } }
    val fofById = remember(fof) { fof.associateBy { it.id } }

    Box(
        modifier = modifier
            .size(size)
            .testTag(PU_GRAPH_TEST_TAG)
            .semantics { isTraversalGroup = true }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics { }
                .pointerInput(layout) {
                    detectTapGestures { offset ->
                        val hit = layout.hitTest(
                            offset.x / density.density,
                            offset.y / density.density,
                            HIT_SLOP_DP
                        )
                        if (hit != null) currentOnClick.value(hit.id)
                    }
                }
        ) {
            drawGraph(
                layout = layout,
                nodes = byId,
                fof = fofById,
                modeFilter = modeFilter,
                selectedId = selectedId,
                dimmedIds = dimmedIds,
                centerLabel = centerLabel,
                colors = colors,
                textMeasurer = textMeasurer,
                labels = size >= PU_GRAPH_MINI_THRESHOLD
            )
        }
        SemanticNodes(layout, byId, fofById, selectedId) { currentOnClick.value(it) }
    }
}

const val PU_GRAPH_TEST_TAG = "PuGraph"

/** Lista semántica paralela: cajas invisibles sobre cada nodo, solo para accesibilidad. */
@Composable
private fun SemanticNodes(
    layout: GraphLayout,
    nodes: Map<String, GraphNode>,
    fof: Map<String, GraphFof>,
    selectedId: String?,
    onClick: (String) -> Unit
) {
    val ordered = layout.nodes.sortedWith(
        compareByDescending<PlacedNode> { nodes[it.id]?.affinity ?: 0 }.thenBy { it.id }
    ) + layout.fof.sortedWith(
        compareByDescending<PlacedNode> { fof[it.id]?.affinity ?: 0 }.thenBy { it.id }
    )
    ordered.forEachIndexed { index, placed ->
        val description = nodes[placed.id]?.let(::graphNodeDescription)
            ?: fof[placed.id]?.let(::graphFofDescription)
            ?: return@forEachIndexed
        val half = placed.radius + HIT_SLOP_DP
        Box(
            modifier = Modifier
                .offset((placed.x - half).dp, (placed.y - half).dp)
                .size((half * 2).dp)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                    selected = placed.id == selectedId
                    traversalIndex = index.toFloat()
                    onClick {
                        onClick(placed.id)
                        true
                    }
                }
        )
    }
}

private fun DrawScope.drawGraph(
    layout: GraphLayout,
    nodes: Map<String, GraphNode>,
    fof: Map<String, GraphFof>,
    modeFilter: GraphMode?,
    selectedId: String?,
    dimmedIds: Set<String>,
    centerLabel: String,
    colors: ParcheUColors,
    textMeasurer: TextMeasurer,
    labels: Boolean
) {
    if (layout.size <= 0f) return
    val u = density // px por dp
    val k = layout.size / RadialLayout.BASE_SIZE
    val strokeK = max(k, 0.6f)
    val center = Offset(layout.centerX * u, layout.centerY * u)
    fun PlacedNode.offset() = Offset(x * u, y * u)
    val placedById = layout.nodes.associateBy { it.id }

    // Anillos punteados "2 5", 1 dp, color ln. Sin fof hay 3; con fof, 3 + la capa exterior.
    val ringCount = if (layout.fof.isEmpty()) 3 else 4
    val ringDash = PathEffect.dashPathEffect(floatArrayOf(2f * u, 5f * u))
    layout.ringRadii.take(ringCount).forEach { r ->
        drawCircle(
            color = colors.ln,
            radius = r * u,
            center = center,
            style = Stroke(width = 1f * u, pathEffect = ringDash)
        )
    }

    // Líneas de amigos de amigos: prt, dash "2 4", .8, desde su conexión de origen.
    val fofDash = PathEffect.dashPathEffect(floatArrayOf(2f * u, 4f * u))
    layout.fof.forEach { f ->
        val parent = fof[f.id]?.parentId?.let { placedById[it] } ?: return@forEach
        drawLine(
            color = colors.prt,
            start = parent.offset(),
            end = f.offset(),
            strokeWidth = 1.5f * u,
            pathEffect = fofDash,
            alpha = GraphAlpha.FOF_EDGE
        )
    }

    // Aristas desde el centro: color del modo; sólida, punteada o tenue según el estado.
    val simDash = PathEffect.dashPathEffect(floatArrayOf(5f * u, 5f * u))
    layout.nodes.forEach { p ->
        val node = nodes[p.id] ?: return@forEach
        val dimmed = isDimmed(node, modeFilter, selectedId, dimmedIds)
        val width = when (node.state) {
            GraphEdgeState.CONNECTED -> 2.5f
            GraphEdgeState.SIMULATED -> 2f
            GraphEdgeState.PENDING -> 1.5f
        }
        drawLine(
            color = modeColor(colors, node.mode),
            start = center,
            end = p.offset(),
            strokeWidth = width * strokeK * u,
            cap = StrokeCap.Round,
            pathEffect = if (node.state == GraphEdgeState.SIMULATED) simDash else null,
            alpha = edgeAlpha(node, dimmed)
        )
    }

    // Nodo central.
    drawCircle(ParcheUPalette.Violet, radius = layout.centerRadius * u, center = center)
    drawCenteredText(
        textMeasurer,
        centerLabel,
        TextStyle(
            color = ParcheUPalette.White,
            fontFamily = SoraFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = toSp(14f * k)
        ),
        x = center.x,
        baseline = center.y + 5f * k * u
    )

    // Conexiones.
    layout.nodes.forEach { p ->
        val node = nodes[p.id] ?: return@forEach
        val dimmed = isDimmed(node, modeFilter, selectedId, dimmedIds)
        withLayerAlpha(nodeAlpha(node, dimmed)) {
            val o = p.offset()
            val r = p.radius * u
            if (node.id == selectedId) {
                drawCircle(
                    color = colors.tx,
                    radius = r + 6f * k * u,
                    center = o,
                    style = Stroke(width = 2f * u)
                )
            }
            val avatar = nodeAvatarColors(node, colors.isDark)
            drawCircle(colors.bg, radius = r, center = o)
            drawCircle(avatar.fill, radius = r, center = o)
            drawCircle(
                color = modeColor(colors, node.mode),
                radius = r,
                center = o,
                style = Stroke(width = 2f * strokeK * u)
            )
        }
        withLayerAlpha(textAlpha(dimmed)) {
            val o = p.offset()
            val r = p.radius * u
            val avatar = nodeAvatarColors(node, colors.isDark)
            drawCenteredText(
                textMeasurer,
                if (node.anonymous) "?" else node.initials,
                TextStyle(
                    color = avatar.content,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = toSp(12.5f * k)
                ),
                x = o.x,
                baseline = o.y + 4.5f * k * u
            )
            if (labels) {
                drawCenteredText(
                    textMeasurer,
                    if (node.anonymous) "Alguien" else node.name,
                    TextStyle(
                        color = colors.mu,
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = toSp(10.5f * k)
                    ),
                    x = o.x,
                    baseline = o.y + r + 13f * k * u
                )
            }
        }
    }

    // Amigos de amigos: r 13, fondo s2, borde prt 1.5 (punteado "3 3" si es anónimo).
    val anonDash = PathEffect.dashPathEffect(floatArrayOf(3f * u, 3f * u))
    layout.fof.forEach { p ->
        val f = fof[p.id] ?: return@forEach
        val o = p.offset()
        val r = p.radius * u
        drawCircle(colors.s2, radius = r, center = o)
        drawCircle(
            color = colors.prt,
            radius = r,
            center = o,
            style = Stroke(width = 1.5f * u, pathEffect = if (f.anonymous) anonDash else null)
        )
        drawCenteredText(
            textMeasurer,
            if (f.anonymous) "?" else f.initials,
            TextStyle(
                color = colors.tx,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = toSp(12f * k)
            ),
            x = o.x,
            baseline = o.y + 4.5f * k * u
        )
        if (labels) {
            drawCenteredText(
                textMeasurer,
                "${if (f.anonymous) "Alguien" else f.name} · ${f.affinity} %",
                TextStyle(
                    color = colors.mu,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = toSp(9.5f * k)
                ),
                x = o.x,
                baseline = o.y + r + 12f * k * u
            )
        }
    }
}

internal fun modeColor(colors: ParcheUColors, mode: GraphMode): Color = when (mode) {
    GraphMode.AMISTAD -> colors.friendship.solid
    GraphMode.PAREJA -> colors.couple.solid
    GraphMode.ESTUDIO -> colors.study.solid
}

/** Tamaño de texto fijo en dp (el grafo no escala con la fuente del sistema; TalkBack lee la lista). */
private fun Density.toSp(dpValue: Float) = dpValue.dp.toSp()

/** Opacidad de grupo como el `<g opacity>` del SVG: compone el nodo entero y luego aplica el alpha. */
private inline fun DrawScope.withLayerAlpha(alpha: Float, block: DrawScope.() -> Unit) {
    if (alpha >= 1f) {
        block()
        return
    }
    drawIntoCanvas { canvas ->
        canvas.saveLayer(
            Rect(Offset.Zero, size),
            Paint().apply { this.alpha = alpha }
        )
        block()
        canvas.restore()
    }
}

/** Dibuja [text] centrado en [x] con la línea base en [baseline] (como `text-anchor: middle`). */
private fun DrawScope.drawCenteredText(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    x: Float,
    baseline: Float
) {
    val result = measurer.measure(text, style, softWrap = false, maxLines = 1)
    drawText(
        textLayoutResult = result,
        topLeft = Offset(x - result.size.width / 2f, baseline - result.firstBaseline)
    )
}
