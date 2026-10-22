package me.santiagoreina.parcheu.api.graph

import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import java.time.Instant
import java.util.UUID

/**
 * Grafo en memoria con la misma semántica que [ExposedGraphRepository] (incluido el filtro de
 * bloqueados y borrados). Sirve para `ENV=local` sin base de datos y para las pruebas de rutas.
 */
class InMemoryGraphRepository : GraphRepository {
    private data class Key(val low: UUID, val high: UUID, val mode: Mode)

    private val lock = Any()
    val people = mutableMapOf<UUID, PersonRow>()
    private val edges = mutableMapOf<Key, EdgeSnapshot>()
    val blocks = mutableSetOf<Pair<UUID, UUID>>()
    val communities = mutableMapOf<UUID, MutableSet<UUID>>()
    val simulations = mutableListOf<Triple<UUID, UUID, SimulationRow>>()

    fun addPerson(person: PersonRow) = synchronized(lock) { people[person.id] = person }

    fun block(blocker: UUID, blocked: UUID) = synchronized(lock) { blocks += blocker to blocked }

    fun unblock(blocker: UUID, blocked: UUID) = synchronized(lock) { blocks -= blocker to blocked }

    /** Inserta o reemplaza una arista directamente (fixtures de pruebas). */
    fun putEdge(a: UUID, b: UUID, mode: Mode, state: EdgeState, affinity: Int?, at: Instant = Instant.EPOCH) = synchronized(lock) {
        val (low, high) = ordered(a, b)
        edges[Key(low, high, mode)] = EdgeSnapshot(state, affinity, null, at, if (state == EdgeState.CONNECTED) at else null)
    }

    fun edge(a: UUID, b: UUID, mode: Mode): EdgeSnapshot? = synchronized(lock) {
        val (low, high) = ordered(a, b)
        edges[Key(low, high, mode)]
    }

    private fun isBlocked(a: UUID, b: UUID) = (a to b) in blocks || (b to a) in blocks

    override suspend fun egoEdges(viewer: UUID, filter: GraphFilter): List<EgoEdgeRow> = synchronized(lock) {
        val community = filter.community
        if (community != null && viewer !in communities[community].orEmpty()) return emptyList()
        edges.mapNotNull { (key, edge) ->
            val other = when (viewer) {
                key.low -> key.high
                key.high -> key.low
                else -> return@mapNotNull null
            }
            val person = people[other] ?: return@mapNotNull null
            when {
                person.deletedAt != null -> null
                isBlocked(viewer, other) -> null
                filter.mode != null && key.mode != filter.mode -> null
                filter.minAffinity != null && (edge.affinity == null || edge.affinity < filter.minAffinity) -> null
                community != null && other !in communities[community].orEmpty() -> null
                else -> EgoEdgeRow(person, key.mode, edge.state, edge.affinity, edge.lastSimulationId, edge.connectedAt)
            }
        }
    }

    override suspend fun connectedWith(viewer: UUID): Set<UUID> = synchronized(lock) {
        edges.filter { (key, edge) -> edge.state == EdgeState.CONNECTED && (key.low == viewer || key.high == viewer) }
            .map { (key, _) -> if (key.low == viewer) key.high else key.low }
            .toSet()
    }

    override suspend fun blockedEitherWay(viewer: UUID): Set<UUID> = synchronized(lock) {
        blocks.mapNotNull { (blocker, blocked) ->
            when (viewer) {
                blocker -> blocked
                blocked -> blocker
                else -> null
            }
        }.toSet()
    }

    override suspend fun person(userId: UUID): PersonRow? = synchronized(lock) { people[userId] }

    override suspend fun lastSimulation(a: UUID, b: UUID): SimulationRow? = synchronized(lock) {
        simulations.filter { (x, y, _) -> (x == a && y == b) || (x == b && y == a) }
            .map { it.third }
            .maxByOrNull { it.createdAt }
    }

    override suspend fun updateEdge(
        a: UUID,
        b: UUID,
        mode: Mode,
        now: Instant,
        change: (EdgeSnapshot?) -> EdgeChange,
    ): EdgeSnapshot? = synchronized(lock) {
        if (isBlocked(a, b)) return null
        val (low, high) = ordered(a, b)
        val key = Key(low, high, mode)
        val existing = edges[key]
        when (val decision = change(existing)) {
            EdgeChange.Keep -> existing
            EdgeChange.Remove -> {
                edges.remove(key)
                null
            }
            is EdgeChange.Save -> {
                edges[key] = decision.edge
                decision.edge
            }
        }
    }

    private fun ordered(a: UUID, b: UUID): Pair<UUID, UUID> = if (a.toString() < b.toString()) a to b else b to a
}
