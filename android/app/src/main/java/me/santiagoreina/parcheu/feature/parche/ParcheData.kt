package me.santiagoreina.parcheu.feature.parche

import kotlinx.serialization.Serializable
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.GraphDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NodeDto
import me.santiagoreina.parcheu.designsystem.graph.GraphEdgeState
import me.santiagoreina.parcheu.designsystem.graph.GraphMode
import me.santiagoreina.parcheu.designsystem.graph.GraphNode

/** Umbral del chip "Afinidad 70 %+". */
const val MIN_AFFINITY_FILTER = 70

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
    initials = initials,
    affinity = affinity,
    mode = mode.toGraphMode(),
    state = state.toGraphState(),
    anonymous = anonymous
)

/** Nodos que se dibujan: con "Afinidad 70 %+" desaparecen los de menos de [MIN_AFFINITY_FILTER]. */
fun ParcheFilters.visibleNodes(nodes: List<NodeDto>): List<NodeDto> =
    if (minAffinity) nodes.filter { it.affinity >= MIN_AFFINITY_FILTER } else nodes

/** Ids de los nodos visibles que se atenúan porque su modo no es el elegido. */
fun ParcheFilters.dimmedIds(nodes: List<NodeDto>): Set<String> = mode?.let { selected ->
    visibleNodes(nodes).filter { it.mode != selected }.map { it.id }.toSet()
}
    ?: emptySet()

/** Solo el grafo sin filtrar se guarda para el pintado instantáneo. */
val ParcheFilters.cacheable: Boolean get() = communityId == null
