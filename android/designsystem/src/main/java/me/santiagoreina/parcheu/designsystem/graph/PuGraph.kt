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
import kotlin.math.max
import me.santiagoreina.parcheu.designsystem.graph.layout.FofNode
import me.santiagoreina.parcheu.designsystem.graph.layout.GraphLayout
import me.santiagoreina.parcheu.designsystem.graph.layout.LayoutNode
import me.santiagoreina.parcheu.designsystem.graph.layout.PlacedNode
import me.santiagoreina.parcheu.designsystem.graph.layout.RadialLayout
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
 * [affinity] es la estimación de ADR-0023.
 */
@Immutable
data class GraphFof(
    val id: String,
    val parentId: String,
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
 * Layout de G0 para un lienzo de [sizeDp] dp. Los amigos de amigos se ordenan por su conexión de
 * origen para que queden agrupados en la capa exterior. Coordenadas en dp.
 */
internal fun computeGraphLayout(
    nodes: List<GraphNode>,
    fof: List<GraphFof>,
    sizeDp: Float
): GraphLayout {
    val modeOf = nodes.associate { it.id to it.mode.name }
    return RadialLayout.compute(
        nodes = nodes.map { LayoutNode(it.id, it.mode.name, it.affinity) },
        size = sizeDp,
        fof = fof.map { FofNode(it.id, "${modeOf[it.parentId].orEmpty()}:${it.parentId}") }
    )
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
        val parent = fof[f.id]?.let { placedById[it.parentId] } ?: return@forEach
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
            val avatar = avatarColors(affinityAlpha(node.affinity), colors.isDark)
            drawCircle(colors.bg, radius = r, center = o)
            drawCircle(avatar.fill, radius = r, center = o)
            drawCircle(
                color = modeColor(colors, node.mode),
                radius = r,
                center = o,
                style = Stroke(width = 2f * strokeK * u)
            )
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
