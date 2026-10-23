package me.santiagoreina.parcheu.api.home

import me.santiagoreina.parcheu.api.graph.EgoEdgeRow
import me.santiagoreina.parcheu.api.graph.GraphFilter
import me.santiagoreina.parcheu.api.graph.GraphRepository
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.graph.PersonView
import me.santiagoreina.parcheu.api.graph.ThirdPartyView
import me.santiagoreina.parcheu.api.graph.ViewContext
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.contract.Suggestion
import java.util.UUID

/**
 * Sugerencias de Inicio (C1, E4.7): volver a simular con conexiones directas (CONNECTED en Amistad o
 * Estudio; Pareja nunca se sugiere). Para ParcheU+ agrega `mutualCount`, el número de amigos en
 * común; para gratis ese dato premium no se calcula ni se envía.
 *
 * Es una vista de terceros (ADR-0012): el conteo de amigos en común lee aristas del candidato con
 * otras personas, así que sigue el patrón de la suite E3.5 (candidatos → `canSee` → `canSeeEdge` →
 * `present`) y está registrada en [me.santiagoreina.parcheu.api.graph.ThirdPartyViews].
 */
class HomeSuggestions(
    private val graph: GraphRepository,
    private val policy: GraphVisibilityPolicy,
) : ThirdPartyView {
    override val viewName = "home-suggestions"

    /** Arista candidato–amigo en común que entró en el conteo (ids públicos para el observador). */
    data class MutualEdge(val candidateId: String, val mutualId: String, val mode: Mode)

    /** Una sugerencia antes de pasar al contrato. [mutuals] es null si no se pidió (gratis). */
    data class Candidate(
        val person: PersonView,
        val mode: Mode,
        val affinity: Int?,
        val mutuals: List<MutualEdge>?,
    )

    /**
     * Candidatos de [viewer] ordenados por afinidad (mayor primero). [exclude] deja fuera personas
     * (p. ej. con quien ya hay una simulación en curso). Con [withMutuals] calcula los amigos en común.
     */
    suspend fun candidates(
        viewer: UUID,
        withMutuals: Boolean,
        exclude: Set<UUID> = emptySet(),
        limit: Int = Int.MAX_VALUE,
    ): List<Candidate> {
        val blocked = graph.blockedEitherWay(viewer)
        // Conexiones directas sin Pareja (la SQL ya quita borrados y bloqueados; la policy es la segunda barrera).
        val direct = graph.egoEdges(viewer, GraphFilter())
            .filter { it.state == EdgeState.CONNECTED && it.mode != Mode.PAREJA }
            .filter { policy.canSee(viewer, it.other, ViewContext.EGO, blocked) }
        val directIds = direct.map { it.other.id }.toSet()
        return direct
            .filter { it.other.id !in exclude }
            .groupBy { it.other.id }
            .values
            // Una sugerencia por persona: la arista de mayor afinidad (Amistad antes que Estudio si empatan).
            .map { edges -> edges.sortedWith(BY_AFFINITY.thenBy { it.mode }).first() }
            .sortedWith(BY_AFFINITY.thenBy { it.other.displayName.orEmpty() })
            .take(limit)
            .map { edge ->
                val view = policy.present(viewer, edge.other, connected = true, context = ViewContext.EGO)
                val mutuals = if (withMutuals) mutualsOf(viewer, edge.other.id, view.id, directIds, blocked) else null
                Candidate(view, edge.mode, edge.affinity, mutuals)
            }
    }

    /** [candidates] traducidos al contrato: `mutualCount` solo con [plus]. */
    suspend fun forHome(viewer: UUID, plus: Boolean, exclude: Set<UUID>, limit: Int = MAX_SUGGESTIONS): List<Suggestion> =
        candidates(viewer, withMutuals = plus, exclude = exclude, limit = limit).map { c ->
            Suggestion(
                other = PersonRef(
                    id = c.person.id,
                    label = c.person.label,
                    initials = c.person.initials,
                    photoUrl = c.person.photoUrl,
                    isDemo = c.person.isDemo,
                ),
                mode = c.mode,
                estimatedAffinity = c.affinity,
                reason = reasonFor(c.affinity),
                mutualCount = c.mutuals?.map { it.mutualId }?.distinct()?.size,
            )
        }

    /**
     * Amigos en común entre [viewer] y [candidate]: personas con CONNECTED (sin Pareja) a los dos. El
     * candidato y cada amigo en común deben ser visibles en segundo grado (no HIDDEN, no bloqueados, no
     * borrados) y la arista candidato–amigo debe poder verse (nunca Pareja ajena).
     */
    private suspend fun mutualsOf(
        viewer: UUID,
        candidate: UUID,
        candidatePublicId: String,
        directIds: Set<UUID>,
        blocked: Set<UUID>,
    ): List<MutualEdge> {
        val candidateRow = graph.person(candidate) ?: return emptyList()
        if (!policy.canSee(viewer, candidateRow, ViewContext.THIRD_PARTY, blocked)) return emptyList()
        return graph.egoEdges(candidate, GraphFilter())
            .asSequence()
            .filter { it.state == EdgeState.CONNECTED && it.mode != Mode.PAREJA }
            .filter { it.other.id != viewer && it.other.id in directIds }
            .filter { policy.canSeeEdge(viewer, candidate, it.other.id, it.mode) }
            .filter { policy.canSee(viewer, it.other, ViewContext.THIRD_PARTY, blocked) }
            .map { edge ->
                val mutual = policy.present(viewer, edge.other, connected = true, context = ViewContext.THIRD_PARTY)
                MutualEdge(candidatePublicId, mutual.id, edge.mode)
            }
            .toList()
    }

    companion object {
        const val MAX_SUGGESTIONS = 3

        private val BY_AFFINITY = compareByDescending<EgoEdgeRow> { it.affinity ?: -1 }

        /** Texto libre sin conteos ni datos premium (contrato de [Suggestion]). */
        fun reasonFor(affinity: Int?): String =
            if (affinity == null) "Ya están conectados: simulen cómo les va" else "La última vez salieron $affinity % afines"
    }
}
