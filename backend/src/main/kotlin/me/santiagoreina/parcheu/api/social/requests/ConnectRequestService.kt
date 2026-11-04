package me.santiagoreina.parcheu.api.social.requests

import me.santiagoreina.parcheu.api.demo.DemoVisibility.withoutDisabledDemos
import me.santiagoreina.parcheu.api.graph.GraphFilter
import me.santiagoreina.parcheu.api.graph.GraphRepository
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.graph.PersonView
import me.santiagoreina.parcheu.api.graph.ThirdPartyView
import me.santiagoreina.parcheu.api.graph.ViewContext
import me.santiagoreina.parcheu.api.platform.Premium
import me.santiagoreina.parcheu.contract.ConnectRequest
import me.santiagoreina.parcheu.contract.ConnectRequestsDto
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.PersonRef
import java.util.UUID

/**
 * 'Quién quiere conectar contigo' (`GET /v1/connect-requests`, E6.6, arquitectura.md §5.2): personas que
 * decidieron conectar contigo en una simulación y a las que aún no respondes (ni conectar ni 'Ahora no').
 *
 * Es una vista de terceros (ADR-0012): saber que alguien quiere conectar es contenido de Plus y la persona
 * todavía no tiene conexión mutua contigo. Sigue el patrón de la suite E3.5 (candidatos → `canSee` →
 * `present`) y está registrada en [me.santiagoreina.parcheu.api.graph.ThirdPartyViews].
 *
 * - Nunca aparecen personas bloqueadas (en cualquier sentido), borradas ni HIDDEN: no se revela que
 *   quien se oculta decidió algo sobre ti.
 * - La persona va siempre enmascarada con su `anonId` ('Alguien de la UIS', sin foto ni uuid): el nombre
 *   se revela solo con la conexión mutua (E4.6), no con esta lista.
 * - Quien ya está conectado contigo en el modo de esa simulación no cuenta (la conexión llegó por otra
 *   simulación). Una sola solicitud por persona: la más reciente.
 * - Gratis recibe solo `count` y los `items` no viajan ([isPlus]); el conteo no revela quién es.
 * - Con [demoUsersEnabled] en falso un usuario demo no aparece (ADR-0027).
 */
class ConnectRequestService(
    private val repo: ConnectRequestRepository,
    private val graph: GraphRepository,
    private val policy: GraphVisibilityPolicy,
    private val isPlus: suspend (UUID) -> Boolean,
    private val demoUsersEnabled: Boolean = true,
) : ThirdPartyView {
    override val viewName = "connect-requests"

    /** Una solicitud visible, con la persona ya pasada por la policy. */
    data class Candidate(val simulationId: UUID, val person: PersonView, val affinity: Int)

    /** Solicitudes visibles de [viewer], más recientes primero, una por persona. */
    suspend fun candidates(viewer: UUID): List<Candidate> {
        val rows = repo.pending(viewer, MAX_REQUESTS)
        if (rows.isEmpty()) return emptyList()
        val blocked = graph.blockedEitherWay(viewer)
        val connected = graph.egoEdges(viewer, GraphFilter())
            .filter { it.state == EdgeState.CONNECTED }
            .map { it.other.id to it.mode }
            .toSet()
        val seen = mutableSetOf<UUID>()
        return rows
            .filter { it.otherId != viewer && (it.otherId to it.mode) !in connected }
            .mapNotNull { row -> graph.person(row.otherId)?.let { row to it } }
            .filter { (_, person) -> policy.canSee(viewer, person, ViewContext.THIRD_PARTY, blocked) }
            .withoutDisabledDemos(demoUsersEnabled) { (_, person) -> person.isDemo }
            .filter { (row, _) -> seen.add(row.otherId) }
            .map { (row, person) ->
                // Sin conexión mutua: siempre enmascarada, aunque su visibilidad sea NAME.
                Candidate(row.simulationId, policy.present(viewer, person, connected = false, context = ViewContext.EGO), row.score)
            }
    }

    @Premium("GET /v1/connect-requests")
    suspend fun requests(viewer: UUID): ConnectRequestsDto {
        val list = candidates(viewer)
        if (!isPlus(viewer)) return ConnectRequestsDto(count = list.size)
        return ConnectRequestsDto(
            count = list.size,
            items = list.map { c ->
                ConnectRequest(
                    simulationId = c.simulationId.toString(),
                    other = PersonRef(
                        anonId = c.person.id,
                        label = c.person.label,
                        initials = c.person.initials,
                        isDemo = c.person.isDemo,
                    ),
                    affinity = c.affinity,
                )
            },
        )
    }

    private companion object {
        const val MAX_REQUESTS = 200
    }
}

