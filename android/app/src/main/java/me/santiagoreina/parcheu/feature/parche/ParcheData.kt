package me.santiagoreina.parcheu.feature.parche

import kotlinx.serialization.Serializable
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.GraphDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NodeDto
import me.santiagoreina.parcheu.designsystem.components.compound.displayInitials
import me.santiagoreina.parcheu.designsystem.graph.GraphEdgeState
import me.santiagoreina.parcheu.designsystem.graph.GraphMode
import me.santiagoreina.parcheu.designsystem.graph.GraphNode
import me.santiagoreina.parcheu.designsystem.theme.AFFINITY_UNMEASURED_LABEL

/** Umbral del chip "Afinidad 70 %+". */
const val MIN_AFFINITY_FILTER = 70

/** Valor grande de D2 cuando la afinidad aún no se ha medido (conexión por invitación sin simular). */
const val AFFINITY_UNMEASURED_VALUE = "—"

/** Texto de TalkBack de la afinidad de D2: "Afinidad 87 %" o "Afinidad: sin simular". */
fun affinityDescription(affinity: Int?): String =
    affinity?.let { "Afinidad $it %" } ?: "Afinidad: ${AFFINITY_UNMEASURED_LABEL.lowercase()}"

/** Lo que D1 necesita para pintarse: el ego-grafo y si la persona tiene ParcheU+ (banner de bloqueo). */
@Serializable
data class ParcheContent(val graph: GraphDto, val plus: Boolean = false)

/** Filtros de D1. El de modo atenúa, el de afinidad oculta y el de comunidad lo resuelve la API. */
data class ParcheFilters(
    val mode: Mode? = null,
    val minAffinity: Boolean = false,
    val communityId: String? = null
)

fun Mode.toGraphMode(): GraphMode = GraphMode.valueOf(name)

fun EdgeState.toGraphState(): GraphEdgeState = GraphEdgeState.valueOf(name)

fun NodeDto.toGraphNode(): GraphNode = GraphNode(
    id = id,
    name = label,
    initials = displayInitials(initials, label),
    affinity = measuredAffinity,
    mode = mode.toGraphMode(),
    state = state.toGraphState(),
    anonymous = anonymous
)

/**
 * Nodos que se dibujan: con "Afinidad 70 %+" desaparecen los de menos de [MIN_AFFINITY_FILTER] y los que
 * aún no tienen afinidad medida (no se sabe si llegan al umbral).
 */
fun ParcheFilters.visibleNodes(nodes: List<NodeDto>): List<NodeDto> = if (minAffinity) {
    nodes.filter { (it.measuredAffinity ?: return@filter false) >= MIN_AFFINITY_FILTER }
} else {
    nodes
}

/** Ids de los nodos visibles que se atenúan porque su modo no es el elegido. */
fun ParcheFilters.dimmedIds(nodes: List<NodeDto>): Set<String> = mode?.let { selected ->
    visibleNodes(nodes).filter { it.mode != selected }.map { it.id }.toSet()
}
    ?: emptySet()

/** Solo el grafo sin filtrar se guarda para el pintado instantáneo. */
val ParcheFilters.cacheable: Boolean get() = communityId == null
