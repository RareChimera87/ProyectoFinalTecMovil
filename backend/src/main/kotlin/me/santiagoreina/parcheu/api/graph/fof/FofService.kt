package me.santiagoreina.parcheu.api.graph.fof

import me.santiagoreina.parcheu.api.demo.DemoVisibility.withoutDisabledDemos
import me.santiagoreina.parcheu.api.graph.GraphFilter
import me.santiagoreina.parcheu.api.graph.GraphRepository
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.graph.PersonView
import me.santiagoreina.parcheu.api.graph.ThirdPartyView
import me.santiagoreina.parcheu.api.graph.ViewContext
import me.santiagoreina.parcheu.api.simulation.SimulationScoring
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.FofCandidate
import me.santiagoreina.parcheu.contract.FofDto
import me.santiagoreina.parcheu.contract.Mode
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Amigos de amigos (`GET /v1/graph/fof`, E6.3, arquitectura.md §6.2): personas a dos saltos por
 * aristas CONNECTED que no son de Pareja, con las que el observador no tiene ninguna arista.
 *
 * Es una vista de terceros (ADR-0012) y sigue el patrón de la suite E3.5: el repositorio ya filtra en
 * SQL y aquí cada candidato vuelve a pasar por [GraphVisibilityPolicy] (`canSee` en segundo grado,
 * `canSeeEdge` para cada arista que lo conecta y `present`, que lo enmascara con su `anonId`). Está
 * registrada en [me.santiagoreina.parcheu.api.graph.ThirdPartyViews].
 *
 * - La afinidad es una **estimación** por similitud de rasgos (ADR-0023); sin rasgos de alguno de los
 *   dos queda en [NEUTRAL_AFFINITY].
 * - Orden: amigos en común (mayor primero), luego la estimación y luego el id público.
 * - El modo del candidato es el de la mayoría de las aristas que lo conectan (Amistad si empatan).
 * - `viaId` (origen de la línea punteada de G0 y "Amigo de …" en D6) es uno de esos amigos en común,
 *   elegido entre las aristas que ya pasaron los dos filtros ([viaOf]); nunca sale de otra consulta.
 * - Gratis recibe solo `count`: los `items` son premium y no viajan ([isPlus]).
 * - Con [demoUsersEnabled] en falso un usuario demo no se propone (ADR-0027).
 */
class FofService(
    private val repo: FofRepository,
    private val graph: GraphRepository,
    private val policy: GraphVisibilityPolicy,
    private val isPlus: suspend (UUID) -> Boolean,
    private val demoUsersEnabled: Boolean = true,
) : ThirdPartyView {
    override val viewName = "fof"

    /** Arista amigo en común–candidato que entró en el conteo (ids públicos para el observador). */
    data class MutualEdge(val candidateId: String, val mutualId: String, val mode: Mode)

    /** Un candidato ya filtrado por la policy, antes de pasar al contrato. */
    data class Candidate(
        val person: PersonView,
        val mutuals: Int,
        val mode: Mode,
        val estimatedAffinity: Int,
        val edges: List<MutualEdge>,
        /** Amigo en común por el que se llega (id público para el observador: `users.id`). */
        val viaId: String,
    )

    /** Todos los candidatos visibles de [viewer] (hasta [MAX_CANDIDATES]) en el orden de la vista. */
    suspend fun candidates(viewer: UUID): List<Candidate> {
        val rows = repo.candidates(viewer, MAX_CANDIDATES)
        if (rows.isEmpty()) return emptyList()
        val blocked = graph.blockedEitherWay(viewer)
        val ego = graph.egoEdges(viewer, GraphFilter())
        val withEdge = ego.map { it.other.id }.toSet()
        // Amigos en común válidos: conexión directa (sin Pareja) visible en segundo grado.
        val friends = ego
            .filter { it.state == EdgeState.CONNECTED && it.mode != Mode.PAREJA }
            .filter { policy.canSee(viewer, it.other, ViewContext.THIRD_PARTY, blocked) }
            .associate { it.other.id to it.other }
        val myTraits = repo.traits(viewer)
        return rows
            .filter { it.person.id != viewer && it.person.id !in withEdge }
            .filter { policy.canSee(viewer, it.person, ViewContext.THIRD_PARTY, blocked) }
            .withoutDisabledDemos(demoUsersEnabled) { it.person.isDemo }
            .mapNotNull { row ->
                val links = row.links
                    .filter { it.mutual in friends && it.mutual != row.person.id }
                    .filter { policy.canSeeEdge(viewer, it.mutual, row.person.id, it.mode) }
                    .distinct()
                val mutuals = links.map { it.mutual }.distinct().size
                if (mutuals == 0) return@mapNotNull null
                val view = policy.present(viewer, row.person, connected = false, context = ViewContext.THIRD_PARTY)
                val mode = dominantMode(links)
                Candidate(
                    person = view,
                    mutuals = mutuals,
                    mode = mode,
                    estimatedAffinity = estimate(myTraits, row.traits),
                    edges = links.map { MutualEdge(view.id, it.mutual.toString(), it.mode) },
                    viaId = viaOf(links, mode).toString(),
                )
            }
            .sortedWith(
                compareByDescending<Candidate> { it.mutuals }
                    .thenByDescending { it.estimatedAffinity }
                    .thenBy { it.person.id },
            )
    }

    /** `FofDto`: `count` para todos; `items` (los primeros [MAX_ITEMS]) solo con ParcheU+. */
    suspend fun fof(viewer: UUID): FofDto {
        val all = candidates(viewer)
        if (!isPlus(viewer)) return FofDto(count = all.size)
        return FofDto(
            count = all.size,
            items = all.take(MAX_ITEMS).map {
                FofCandidate(
                    anonId = it.person.id,
                    label = it.person.label,
                    estimatedAffinity = it.estimatedAffinity,
                    mutuals = it.mutuals,
                    mode = it.mode,
                    viaId = it.viaId,
                )
            },
        )
    }

    companion object {
        /** Lista de D6 (arquitectura.md §6.2, `LIMIT 30`). */
        const val MAX_ITEMS = 30

        /**
         * Filas que se leen del repositorio: más que [MAX_ITEMS] para que el desempate por afinidad
         * estimada en el corte de 30 sea correcto y para que `count` sea útil; es un techo de costo.
         */
        const val MAX_CANDIDATES = 200

        /** Estimación cuando alguno de los dos aún no tiene rasgos. */
        const val NEUTRAL_AFFINITY = 50

        /** Similitud de rasgos de ADR-0023 (la parte determinista del puntaje de simulación). */
        fun estimate(a: List<Double>?, b: List<Double>?): Int {
            if (a == null || b == null || a.isEmpty() || a.size != b.size) return NEUTRAL_AFFINITY
            return SimulationScoring.traitSimilarity(a, b).roundToInt()
        }

        /**
         * Amigo en común por el que se llega al candidato. Sale solo de [links], que ya son aristas
         * CONNECTED que no son de Pareja de la misma fila de la consulta §6.2 y pasaron `canSeeEdge`:
         * nunca de una subconsulta aparte, que podría revelar una arista de Pareja ajena (ADR-0012,
         * backlog E3.4 "Hueco de contrato"). Prefiere una arista del modo del candidato y desempata
         * por id para que sea estable. [links] no puede estar vacío.
         */
        fun viaOf(links: List<FofLink>, mode: Mode): UUID =
            links.filter { it.mode == mode }.ifEmpty { links }
                .map { it.mutual }
                .minBy { it.toString() }

        /** Modo de la mayoría de las aristas que conectan; Amistad si empatan. */
        fun dominantMode(links: List<FofLink>): Mode {
            val estudio = links.count { it.mode == Mode.ESTUDIO }
            val amistad = links.count { it.mode == Mode.AMISTAD }
            return if (estudio > amistad) Mode.ESTUDIO else Mode.AMISTAD
        }
    }
}
