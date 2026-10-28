package me.santiagoreina.parcheu.api.graph.communities

import me.santiagoreina.parcheu.api.demo.DemoVisibility.withoutDisabledDemos
import me.santiagoreina.parcheu.api.graph.GraphFilter
import me.santiagoreina.parcheu.api.graph.GraphRepository
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.graph.PersonView
import me.santiagoreina.parcheu.api.graph.ThirdPartyView
import me.santiagoreina.parcheu.api.graph.ViewContext
import me.santiagoreina.parcheu.contract.CommunitiesDto
import me.santiagoreina.parcheu.contract.CommunityDto
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.PersonRef
import java.util.UUID

/**
 * Comunidades que ve un usuario (`GET /v1/communities`, E6.4): solo aquellas de las que es miembro.
 *
 * Es una vista de terceros (ADR-0012): cada miembro vuelve a pasar por [GraphVisibilityPolicy] (`canSee`
 * en segundo grado y `present`, que lo enmascara con su `anonId` si no hay conexión mutua). Quien no es
 * visible (HIDDEN, borrado, bloqueado, demo apagado) ni se lista ni se cuenta en `size`, y una comunidad
 * con menos de [CommunityDetector.MIN_MEMBERS] personas visibles (con el observador) no se muestra.
 * Gratis recibe solo `count` (el conteo bloqueado de D8); `items` son premium y no viajan.
 */
class CommunitiesView(
    private val repo: CommunityRepository,
    private val graph: GraphRepository,
    private val policy: GraphVisibilityPolicy,
    private val isPlus: suspend (UUID) -> Boolean,
    private val demoUsersEnabled: Boolean = true,
) : ThirdPartyView {
    override val viewName = "communities"

    /** Una comunidad ya filtrada por la policy, antes de pasar al contrato. */
    data class Visible(
        val id: UUID,
        val name: String,
        val members: List<PersonView>,
        /** Miembros visibles incluido el observador. */
        val size: Int,
        val traits: List<String>,
    )

    suspend fun communities(viewer: UUID): List<Visible> {
        val mine = repo.ofUser(viewer)
        if (mine.isEmpty()) return emptyList()
        val blocked = graph.blockedEitherWay(viewer)
        val connected = graph.egoEdges(viewer, GraphFilter()).filter { it.state == EdgeState.CONNECTED }.map { it.other.id }.toSet()
        return mine.mapNotNull { community ->
            val people = community.members.filter { it != viewer }.sorted().mapNotNull { graph.person(it) }
                .filter { policy.canSee(viewer, it, ViewContext.THIRD_PARTY, blocked) }
                .withoutDisabledDemos(demoUsersEnabled) { it.isDemo }
            val size = people.size + 1
            if (size < CommunityDetector.MIN_MEMBERS) return@mapNotNull null
            val views = people.map { policy.present(viewer, it, connected = it.id in connected, context = ViewContext.THIRD_PARTY) }
            val visibleIds = people.map { it.id }.toSet() + viewer
            Visible(
                id = community.id,
                name = community.name,
                members = views,
                size = size,
                traits = CommunityProfile.of(size, repo.traitsOf(visibleIds), emptyList()).traits,
            )
        }.sortedWith(compareByDescending<Visible> { it.size }.thenBy { it.id })
    }

    /** `CommunitiesDto`: `count` para todos; `items` solo con ParcheU+. */
    suspend fun communitiesDto(viewer: UUID): CommunitiesDto {
        val all = communities(viewer)
        if (!isPlus(viewer)) return CommunitiesDto(count = all.size)
        return CommunitiesDto(
            count = all.size,
            items = all.map { community ->
                CommunityDto(
                    id = community.id.toString(),
                    name = community.name,
                    members = community.members.map { it.toRef() },
                    size = community.size,
                    traits = community.traits,
                )
            },
        )
    }

    private fun PersonView.toRef(): PersonRef {
        val real = runCatching { UUID.fromString(id) }.isSuccess
        return PersonRef(
            id = if (real) id else null,
            anonId = if (real) null else id,
            label = label,
            initials = initials,
            photoUrl = photoUrl,
            isDemo = isDemo,
        )
    }
}
