package me.santiagoreina.parcheu.api.social.introductions

import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.GraphFilter
import me.santiagoreina.parcheu.api.graph.GraphRepository
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.graph.ThirdPartyView
import me.santiagoreina.parcheu.api.graph.ViewContext
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.contract.Visibility
import java.util.UUID

/**
 * Vista de terceros de las presentaciones (E6.2, ADR-0012): decide cómo ve una persona presentada a la
 * otra (E3) y qué amigos de amigos puede pedir que le presenten (D6). Sigue el patrón de la suite de
 * invariantes: candidatos → `canSee` → `present`, con [GraphVisibilityPolicy] como única fuente de verdad.
 *
 * - La otra persona es de segundo grado mientras no estén conectados: nombre solo con visibilidad NAME,
 *   nunca HIDDEN, nunca bloqueados ni borrados. Con conexión directa ya es su ego-grafo.
 * - Amigos de amigos: dos saltos por aristas CONNECTED que **no** son de Pareja (nunca se usa una arista
 *   de Pareja ajena), sin HIDDEN, bloqueados ni borrados, ni personas ya conectadas con quien pide.
 */
class IntroductionPeople(
    private val graph: GraphRepository,
    private val policy: GraphVisibilityPolicy,
    private val anonIds: AnonIdSigner,
) : ThirdPartyView {
    override val viewName: String = "introductions"

    /**
     * Cómo ve [viewer] a [subjectId], la otra persona de su presentación. Null si no puede verla (HIDDEN
     * sin conexión, bloqueo en cualquier sentido o cuenta borrada).
     */
    suspend fun presentOther(viewer: UUID, subjectId: UUID): PersonRef? {
        val subject = graph.person(subjectId) ?: return null
        val connected = subjectId in graph.connectedWith(viewer)
        val context = if (connected) ViewContext.EGO else ViewContext.THIRD_PARTY
        if (!policy.canSee(viewer, subject, context, graph.blockedEitherWay(viewer))) return null
        return policy.present(viewer, subject, connected, context).let { view ->
            PersonRef(
                // Antes de la conexión mutua nunca viajan el uuid ni la foto (arquitectura.md §5.2).
                id = view.id.takeIf { connected },
                anonId = if (connected) null else view.id,
                label = view.label,
                initials = view.initials,
                photoUrl = view.photoUrl.takeIf { connected },
                isDemo = view.isDemo,
            )
        }
    }

    /** ¿Se puede presentar a [subject] a un tercero? Nunca a alguien HIDDEN (no aparece en segundo grado). */
    fun acceptsIntroductions(subject: PersonRow): Boolean =
        subject.deletedAt == null && subject.visibility != Visibility.HIDDEN

    /**
     * Amigos de amigos de [requester]: candidato → amigos en común (por quienes se puede pedir la
     * presentación). Solo aristas CONNECTED de Amistad o Estudio.
     */
    suspend fun friendsOfFriends(requester: UUID): Map<UUID, Set<UUID>> {
        val blocked = graph.blockedEitherWay(requester)
        val direct = graph.connectedWith(requester)
        val friends = graph.egoEdges(requester, GraphFilter())
            .filter { it.state == EdgeState.CONNECTED && it.mode != Mode.PAREJA }
            .map { it.other }
            .filter { policy.canSee(requester, it, ViewContext.EGO, blocked) }
            // Misma regla que D6 (FofService): un amigo HIDDEN no cuenta como amigo en común.
            .filter { policy.canSee(requester, it, ViewContext.THIRD_PARTY, blocked) }
            .distinctBy { it.id }
        val result = mutableMapOf<UUID, MutableSet<UUID>>()
        for (friend in friends) {
            graph.egoEdges(friend.id, GraphFilter())
                .filter { it.state == EdgeState.CONNECTED && it.mode != Mode.PAREJA && policy.canSeeEdge(requester, friend.id, it.other.id, it.mode) }
                .map { it.other }
                .filter { it.id != requester && it.id !in direct && policy.canSee(requester, it, ViewContext.THIRD_PARTY, blocked) }
                .forEach { candidate -> result.getOrPut(candidate.id) { mutableSetOf() } += friend.id }
        }
        return result
    }

    /** Resuelve el `anonId` que [requester] ve en D6 entre sus amigos de amigos; null si no es uno de ellos. */
    suspend fun resolveFof(requester: UUID, anonId: String): Pair<UUID, Set<UUID>>? {
        if (!anonId.startsWith(AnonIdSigner.PREFIX)) return null
        return friendsOfFriends(requester).entries.firstOrNull { anonIds.anonId(requester, it.key) == anonId }?.toPair()
    }
}
