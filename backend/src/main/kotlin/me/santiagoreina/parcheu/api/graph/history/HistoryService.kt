package me.santiagoreina.parcheu.api.graph.history

import me.santiagoreina.parcheu.api.graph.EgoEdgeRow
import me.santiagoreina.parcheu.api.graph.GraphFilter
import me.santiagoreina.parcheu.api.graph.GraphRepository
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.api.graph.ViewContext
import me.santiagoreina.parcheu.api.platform.BogotaZone
import me.santiagoreina.parcheu.api.platform.errors.PlusRequired
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.HistoryDto
import me.santiagoreina.parcheu.contract.Moment
import me.santiagoreina.parcheu.contract.MomentKind
import me.santiagoreina.parcheu.contract.MonthSnapshot
import me.santiagoreina.parcheu.contract.PersonRef
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

/**
 * Evolución de la red (`GET /v1/graph/history`, E6.5, arquitectura.md §6.4), solo ParcheU+ (402
 * `PLUS_REQUIRED` si no). No guarda snapshots: se deriva en cada consulta de `edges.created_at` y
 * `connected_at` agrupando por mes de Bogotá.
 *
 * Solo lee aristas directas del observador, con los mismos filtros del ego-grafo (`canSee` y
 * `canSeeEdge`), así que no es una vista de terceros (ADR-0012). El nombre de la otra persona en un
 * momento pasa por [GraphVisibilityPolicy.present] como en `GET /v1/graph`.
 *
 * - `nodes`: personas con alguna arista nacida hasta el fin de ese mes.
 * - `connections`: personas con alguna arista CONNECTED hasta el fin de ese mes (acumulado).
 * - `newConnections`: personas cuya primera conexión cae en ese mes.
 * - La serie va del mes del primer evento hasta el mes actual, sin huecos.
 */
class HistoryService(
    private val repo: GraphRepository,
    private val policy: GraphVisibilityPolicy,
    private val isPlus: suspend (UUID) -> Boolean,
    private val clock: Clock,
) {
    private class Link(val person: PersonRow, val createdAt: Instant?, val connectedAt: Instant?, val affinity: Int?, val connected: Boolean)

    suspend fun history(viewer: UUID): HistoryDto {
        if (!isPlus(viewer)) throw PlusRequired("La evolución de la red es de ParcheU+")
        val blocked = repo.blockedEitherWay(viewer)
        val rows = repo.egoEdges(viewer, GraphFilter())
            .filter { policy.canSee(viewer, it.other, ViewContext.EGO, blocked) }
            .filter { policy.canSeeEdge(viewer, viewer, it.other.id, it.mode) }
        val people = rows.groupBy { it.other.id }.mapValues { (_, edges) -> link(edges) }.values.toList()
        val connectedLinks = people.filter { it.connected }.sortedBy { it.connectedAt ?: it.createdAt ?: Instant.EPOCH }
        val firstCommunity = repo.firstCommunityAt(viewer)
        return HistoryDto(
            months = months(people),
            moments = moments(viewer, rows, connectedLinks, firstCommunity),
        )
    }

    private fun link(edges: List<EgoEdgeRow>): Link {
        val connected = edges.filter { it.state == EdgeState.CONNECTED }
        return Link(
            person = edges.first().other,
            createdAt = edges.mapNotNull { it.createdAt }.minOrNull(),
            connectedAt = connected.mapNotNull { it.connectedAt ?: it.createdAt }.minOrNull(),
            affinity = edges.mapNotNull { it.affinity }.maxOrNull(),
            connected = connected.isNotEmpty(),
        )
    }

    private fun monthOf(at: Instant): YearMonth = YearMonth.from(at.atZone(BogotaZone))

    private fun months(people: List<Link>): List<MonthSnapshot> {
        val events = people.flatMap { listOfNotNull(it.createdAt, it.connectedAt) }
        if (events.isEmpty()) return emptyList()
        val first = monthOf(events.min())
        val last = maxOf(monthOf(clock.instant()), monthOf(events.max()))
        val result = mutableListOf<MonthSnapshot>()
        var month = first
        while (month <= last) {
            val end = month.plusMonths(1).atDay(1).atStartOfDay(BogotaZone).toInstant()
            result += MonthSnapshot(
                month = month.toString(),
                connections = people.count { it.connected && (it.connectedAt ?: it.createdAt)!! < end },
                newConnections = people.count { it.connected && monthOf((it.connectedAt ?: it.createdAt)!!) == month },
                nodes = people.count { (it.createdAt ?: it.connectedAt)!! < end },
            )
            month = month.plusMonths(1)
        }
        return result
    }

    private fun moments(viewer: UUID, rows: List<EgoEdgeRow>, connected: List<Link>, firstCommunity: Instant?): List<Moment> {
        val connectedIds = connected.map { it.person.id }.toSet()
        fun ref(person: PersonRow): PersonRef {
            val view = policy.present(viewer, person, connected = person.id in connectedIds, context = ViewContext.EGO)
            return PersonRef(
                id = view.id.takeIf { !view.anonymous },
                anonId = view.id.takeIf { view.anonymous },
                label = view.label,
                initials = view.initials,
                photoUrl = view.photoUrl,
                isDemo = view.isDemo,
            )
        }
        fun at(link: Link) = (link.connectedAt ?: link.createdAt)!!
        val out = mutableListOf<Moment>()
        connected.firstOrNull()?.let {
            out += Moment(MomentKind.FIRST_CONNECTION, at(it).toKotlin(), "Tu primera conexión", other = ref(it.person))
        }
        firstCommunity?.let { out += Moment(MomentKind.FIRST_COMMUNITY, it.toKotlin(), "Tu primera comunidad") }
        for ((kind, n) in MILESTONES) {
            connected.getOrNull(n - 1)?.let { out += Moment(kind, at(it).toKotlin(), "$n conexiones", value = n) }
        }
        // La afinidad más alta de cualquier arista con puntaje; desempata la más antigua y luego el id.
        rows.filter { it.affinity != null }
            .maxWithOrNull(
                compareBy<EgoEdgeRow> { it.affinity }
                    .thenByDescending { it.connectedAt ?: it.createdAt ?: Instant.MAX }
                    .thenByDescending { it.other.id.toString() },
            )?.let { best ->
                out += Moment(
                    MomentKind.HIGHEST_AFFINITY,
                    (best.connectedAt ?: best.createdAt ?: clock.instant()).toKotlin(),
                    "Tu afinidad más alta: ${best.affinity}%",
                    value = best.affinity,
                    other = ref(best.other),
                )
            }
        return out.sortedBy { it.at }
    }

    companion object {
        private val MILESTONES = listOf(
            MomentKind.CONNECTIONS_10 to 10,
            MomentKind.CONNECTIONS_25 to 25,
            MomentKind.CONNECTIONS_50 to 50,
        )

        private fun Instant.toKotlin() = kotlin.time.Instant.fromEpochSeconds(epochSecond, nano)
    }
}
