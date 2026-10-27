package me.santiagoreina.parcheu.api.graph.communities

import me.santiagoreina.parcheu.api.graph.InMemoryGraphRepository
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import java.time.LocalDate
import java.util.UUID

/**
 * Datos de comunidades (E6.4). La implementación real es [ExposedCommunityRepository];
 * [InMemoryCommunityRepository] replica su semántica para pruebas de rutas y la suite de privacidad.
 */
interface CommunityRepository {
    /**
     * Aristas CONNECTED de Amistad y Estudio entre personas no HIDDEN ni borradas y sin bloqueo entre
     * ellas (filtros en SQL, ADR-0012; [CommunityDetector] los vuelve a aplicar).
     */
    suspend fun connectedEdges(): List<CommunityEdge>

    /** Todas las comunidades guardadas (la tabla conserva la última corrida de cada una). */
    suspend fun all(): List<StoredCommunity>

    suspend fun find(id: UUID): StoredCommunity?

    /** Comunidades de las que [userId] es miembro. */
    suspend fun ofUser(userId: UUID): List<StoredCommunity>

    /** Aplica el [plan] de la semana [week] en una sola transacción. */
    suspend fun apply(week: LocalDate, plan: CommunityPlan)

    suspend fun rename(id: UUID, name: String)

    /** Rasgos de los gemelos de [users] que ya respondieron las preguntas. */
    suspend fun traitsOf(users: Collection<UUID>): List<List<Double>>
}

/** Comunidades en memoria sobre un [InMemoryGraphRepository]; los miembros también alimentan su filtro `community`. */
class InMemoryCommunityRepository(
    private val graph: InMemoryGraphRepository,
    private val traitsOf: (UUID) -> List<Double>? = { null },
) : CommunityRepository {
    private val lock = Any()
    private val stored = linkedMapOf<UUID, StoredCommunity>()

    override suspend fun connectedEdges(): List<CommunityEdge> {
        val blocked = graph.blocks.toSet()
        return graph.allEdges()
            .filter { it.state == EdgeState.CONNECTED && it.mode != Mode.PAREJA }
            .mapNotNull { edge ->
                val a = graph.people[edge.low] ?: return@mapNotNull null
                val b = graph.people[edge.high] ?: return@mapNotNull null
                when {
                    a.deletedAt != null || b.deletedAt != null -> null
                    a.visibility == Visibility.HIDDEN || b.visibility == Visibility.HIDDEN -> null
                    (a.id to b.id) in blocked || (b.id to a.id) in blocked -> null
                    else -> CommunityEdge(a, b, edge.mode, edge.affinity)
                }
            }
    }

    override suspend fun all(): List<StoredCommunity> = synchronized(lock) { stored.values.toList() }

    override suspend fun find(id: UUID): StoredCommunity? = synchronized(lock) { stored[id] }

    override suspend fun ofUser(userId: UUID): List<StoredCommunity> =
        synchronized(lock) { stored.values.filter { userId in it.members } }

    override suspend fun apply(week: LocalDate, plan: CommunityPlan) = synchronized(lock) {
        plan.removed.forEach { stored.remove(it); graph.communities.remove(it) }
        (plan.kept + plan.created).forEach {
            stored[it.id] = it.copy(week = week)
            graph.communities[it.id] = it.members.toMutableSet()
        }
    }

    override suspend fun rename(id: UUID, name: String) = synchronized(lock) {
        stored[id]?.let { stored[id] = it.copy(name = name) }
        Unit
    }

    override suspend fun traitsOf(users: Collection<UUID>): List<List<Double>> = users.mapNotNull(traitsOf)
}
