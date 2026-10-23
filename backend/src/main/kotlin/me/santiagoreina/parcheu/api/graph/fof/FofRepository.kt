package me.santiagoreina.parcheu.api.graph.fof

import me.santiagoreina.parcheu.api.graph.GraphFilter
import me.santiagoreina.parcheu.api.graph.GraphRepository
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import java.util.UUID

/** Arista CONNECTED (sin Pareja) entre un amigo directo del observador ([mutual]) y el candidato. */
data class FofLink(val mutual: UUID, val mode: Mode)

/**
 * Un candidato a dos saltos tal como lo devuelve la consulta de arquitectura.md §6.2: la persona,
 * `count(DISTINCT amigo en común)` y las aristas que lo conectan con esos amigos. [traits] son los
 * rasgos de su gemelo (null si aún no respondió las preguntas).
 */
data class FofRow(
    val person: PersonRow,
    val mutuals: Int,
    val links: List<FofLink>,
    val traits: List<Double>?,
)

/**
 * Consulta de amigos de amigos (E6.3). La implementación real es [ExposedFofRepository] (SQL de
 * arquitectura.md §6.2); [InMemoryFofRepository] replica su semántica sobre un [GraphRepository].
 *
 * Filtros (en SQL, ADR-0012): solo aristas CONNECTED que no son de Pareja en los dos saltos; el
 * amigo en común no está borrado, no es HIDDEN y no tiene bloqueo con el observador; el candidato no
 * es el observador, no está borrado ni es HIDDEN, no tiene **ninguna** arista con el observador
 * (incluida Pareja) y no tiene bloqueo con el observador ni con el amigo en común.
 */
interface FofRepository {
    /** Candidatos ordenados por amigos en común (mayor primero) y luego por id; como mucho [limit]. */
    suspend fun candidates(viewer: UUID, limit: Int): List<FofRow>

    /** Rasgos del gemelo de [userId], o null si no tiene. */
    suspend fun traits(userId: UUID): List<Double>?
}

/**
 * Amigos de amigos en memoria (`ENV=local`, pruebas de rutas y sondas de la suite E3.5) sobre las
 * mismas lecturas del ego-grafo, que ya quitan borrados y bloqueados respecto de quien consulta.
 */
class InMemoryFofRepository(
    private val graph: GraphRepository,
    private val traitsOf: (UUID) -> List<Double>? = { null },
) : FofRepository {

    override suspend fun candidates(viewer: UUID, limit: Int): List<FofRow> {
        val mine = graph.egoEdges(viewer, GraphFilter())
        val withEdge = mine.map { it.other.id }.toSet()
        val blocked = graph.blockedEitherWay(viewer)
        val friends = mine
            .filter { it.state == EdgeState.CONNECTED && it.mode != Mode.PAREJA && it.other.visibility != Visibility.HIDDEN }
            .map { it.other.id }
            .toSortedSet(compareBy { it.toString() })
        val people = mutableMapOf<UUID, PersonRow>()
        val links = mutableMapOf<UUID, MutableSet<FofLink>>()
        for (friend in friends) {
            // egoEdges del amigo ya quita a los borrados y a quien tenga bloqueo con él.
            graph.egoEdges(friend, GraphFilter())
                .filter { it.state == EdgeState.CONNECTED && it.mode != Mode.PAREJA }
                .filter { it.other.id != viewer && it.other.visibility != Visibility.HIDDEN && it.other.deletedAt == null }
                .filter { it.other.id !in withEdge && it.other.id !in blocked }
                .forEach { edge ->
                    people[edge.other.id] = edge.other
                    links.getOrPut(edge.other.id) { linkedSetOf() } += FofLink(friend, edge.mode)
                }
        }
        return links.map { (id, l) ->
            FofRow(people.getValue(id), l.map { it.mutual }.distinct().size, l.toList(), traitsOf(id))
        }
            .sortedWith(compareByDescending<FofRow> { it.mutuals }.thenBy { it.person.id.toString() })
            .take(limit)
    }

    override suspend fun traits(userId: UUID): List<Double>? = traitsOf(userId)
}
