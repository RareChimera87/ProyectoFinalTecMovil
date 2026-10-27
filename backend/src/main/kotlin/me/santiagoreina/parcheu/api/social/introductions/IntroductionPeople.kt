package me.santiagoreina.parcheu.api.social.introductions

import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.GraphRepository
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.graph.fof.FofService
import me.santiagoreina.parcheu.api.graph.ThirdPartyView
import me.santiagoreina.parcheu.api.graph.ViewContext
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
 * - Amigos de amigos: los de D6, sin definición propia: se delega en [FofService] (E6.3).
 */
class IntroductionPeople(
    private val graph: GraphRepository,
    private val policy: GraphVisibilityPolicy,
    private val fof: FofService,
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
     * Amigos de amigos de [requester]: los mismos candidatos que D6 (E6.3), porque [fof] es la única
     * fuente de verdad (ADR-0012 regla 2). Así 'Pedir presentación' nunca acepta a quien D6 no lista
     * (HIDDEN como amigo en común, personas con cualquier arista con quien pide) ni elige un amigo
     * distinto del que D6 nombra ([FofService.Candidate.viaId]).
     */
    suspend fun friendsOfFriends(requester: UUID): List<FofService.Candidate> = fof.candidates(requester)

    /**
     * Resuelve el `anonId` que [requester] ve en D6; null si D6 no lo lista. Devuelve a quién se le pide
     * la presentación: el `viaId` del candidato.
     */
    suspend fun resolveFof(requester: UUID, anonId: String): Pair<UUID, UUID>? {
        if (!anonId.startsWith(AnonIdSigner.PREFIX)) return null
        val candidate = friendsOfFriends(requester).firstOrNull { it.person.id == anonId } ?: return null
        return candidate.userId to UUID.fromString(candidate.viaId)
    }
}
