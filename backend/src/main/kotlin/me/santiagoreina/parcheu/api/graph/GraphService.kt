package me.santiagoreina.parcheu.api.graph

import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.contract.EdgeDto
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.GraphDto
import me.santiagoreina.parcheu.contract.GraphStats
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NodeDetail
import me.santiagoreina.parcheu.contract.NodeDto
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.contract.SimulationSummary
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Ego-grafo del usuario (`GET /v1/graph`) y detalle de un nodo (`GET /v1/graph/nodes/{id}`).
 *
 * El repositorio ya filtra en SQL bloqueados y borrados; aquí se vuelve a filtrar con
 * [GraphVisibilityPolicy], que además decide nombre o "Alguien". Un nodo por persona: su estado,
 * modo y afinidad son los de su arista principal (CONNECTED > PENDING > SIMULATED y luego mayor
 * afinidad). `stats` describe el subgrafo filtrado y cuenta personas: `connections` solo las que
 * tienen alguna arista CONNECTED.
 */
class GraphService(
    private val repo: GraphRepository,
    private val policy: GraphVisibilityPolicy,
) {
    suspend fun graph(viewer: UUID, filter: GraphFilter): GraphDto {
        val me = repo.person(viewer) ?: throw NotFound("Usuario sin perfil")
        val nodes = visibleNodes(viewer, filter)
        val edges = nodes.flatMap { node -> node.edges.map { EdgeDto(node.view.id, it.mode, it.state, it.affinity ?: 0) } }
        return GraphDto(
            me = meNode(me),
            nodes = nodes.map { it.toDto() },
            edges = edges,
            stats = stats(nodes),
        )
    }

    /** [nodeId] es el `id` que devolvió `GET /v1/graph` (uuid si hay conexión, `anonId` si no). */
    suspend fun node(viewer: UUID, nodeId: String): NodeDetail {
        val node = visibleNodes(viewer, GraphFilter()).firstOrNull { it.view.id == nodeId }
            ?: throw NotFound("El nodo no existe o no es visible")
        val last = repo.lastSimulation(viewer, node.person.id)
        return NodeDetail(
            node = node.toDto(),
            lastSimulation = last?.let { sim ->
                SimulationSummary(
                    id = sim.id.toString(),
                    status = sim.status,
                    mode = sim.mode,
                    other = node.view.toPersonRef(),
                    createdAt = kotlin.time.Instant.fromEpochSeconds(sim.createdAt.epochSecond, sim.createdAt.nano),
                    origin = sim.origin,
                    score = sim.score,
                )
            },
            sharedModes = node.edges.map { it.mode }.distinct().sorted(),
        )
    }

    private suspend fun visibleNodes(viewer: UUID, filter: GraphFilter): List<GraphNode> {
        val rows = repo.egoEdges(viewer, filter)
        if (rows.isEmpty()) return emptyList()
        val blocked = repo.blockedEitherWay(viewer)
        val connected = repo.connectedWith(viewer)
        return rows
            .filter { policy.canSee(viewer, it.other, ViewContext.EGO, blocked) }
            .filter { policy.canSeeEdge(viewer, viewer, it.other.id, it.mode) }
            .groupBy { it.other.id }
            .map { (id, edges) ->
                val person = edges.first().other
                GraphNode(
                    person = person,
                    view = policy.present(viewer, person, connected = id in connected, context = ViewContext.EGO),
                    edges = edges.sortedWith(PRIMARY_FIRST),
                )
            }
            .sortedWith(compareBy<GraphNode> { STATE_RANK.getValue(it.primary.state) }.thenByDescending { it.primary.affinity ?: -1 }.thenBy { it.view.id })
    }

    private fun stats(nodes: List<GraphNode>): GraphStats {
        val affinities = nodes.flatMap { node -> node.edges.mapNotNull { it.affinity } }
        return GraphStats(
            connections = nodes.count { node -> node.edges.any { it.state == EdgeState.CONNECTED } },
            simulated = nodes.count { it.primary.state == EdgeState.SIMULATED },
            pending = nodes.count { it.primary.state == EdgeState.PENDING },
            avgAffinity = if (affinities.isEmpty()) 0 else affinities.average().roundToInt(),
        )
    }

    private fun meNode(me: PersonRow): NodeDto {
        val name = me.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: ME_LABEL
        return NodeDto(
            id = me.id.toString(),
            label = name,
            initials = GraphVisibilityPolicy.initialsOf(name),
            photoUrl = me.photoUrl,
            affinity = 100,
            mode = Mode.AMISTAD,
            state = EdgeState.CONNECTED,
            anonymous = false,
        )
    }

    private class GraphNode(val person: PersonRow, val view: PersonView, val edges: List<EgoEdgeRow>) {
        val primary: EgoEdgeRow get() = edges.first()

        fun toDto() = NodeDto(
            id = view.id,
            label = view.label,
            initials = view.initials,
            photoUrl = view.photoUrl,
            affinity = primary.affinity ?: 0,
            mode = primary.mode,
            state = primary.state,
            anonymous = view.anonymous,
        )
    }

    companion object {
        const val ME_LABEL = "Tú"

        private val STATE_RANK = mapOf(EdgeState.CONNECTED to 0, EdgeState.PENDING to 1, EdgeState.SIMULATED to 2)

        private val PRIMARY_FIRST: Comparator<EgoEdgeRow> =
            compareBy<EgoEdgeRow> { STATE_RANK.getValue(it.state) }.thenByDescending { it.affinity ?: -1 }.thenBy { it.mode }

        /** Valida y convierte los parámetros de consulta (400 VALIDATION si alguno es inválido). */
        fun parseFilter(mode: String?, minAffinity: String?, community: String?): GraphFilter {
            val parsedMode = mode?.takeIf { it.isNotBlank() }?.let { raw ->
                Mode.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
                    ?: throw ValidationFailed("mode inválido: '$raw' (AMISTAD, PAREJA o ESTUDIO)")
            }
            val parsedMin = minAffinity?.takeIf { it.isNotBlank() }?.let { raw ->
                raw.trim().toIntOrNull()?.takeIf { it in 0..100 } ?: throw ValidationFailed("minAffinity debe ser un entero de 0 a 100")
            }
            val parsedCommunity = community?.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching { UUID.fromString(raw.trim()) }.getOrElse { throw ValidationFailed("community debe ser un id válido") }
            }
            return GraphFilter(parsedMode, parsedMin, parsedCommunity)
        }
    }
}

private fun PersonView.toPersonRef() = PersonRef(
    id = if (anonymous) null else id,
    anonId = if (anonymous) id else null,
    label = label,
    initials = initials,
    photoUrl = photoUrl,
    isDemo = isDemo,
)
