package me.santiagoreina.parcheu.api.privacy

import me.santiagoreina.parcheu.api.graph.AnonIdSigner
import me.santiagoreina.parcheu.api.graph.GraphFilter
import me.santiagoreina.parcheu.api.graph.GraphService
import me.santiagoreina.parcheu.api.graph.GraphVisibilityPolicy
import me.santiagoreina.parcheu.api.graph.ThirdPartyView
import me.santiagoreina.parcheu.api.graph.ViewContext
import me.santiagoreina.parcheu.api.home.HomeSuggestions
import me.santiagoreina.parcheu.contract.Mode
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Adaptador de la suite para una vista: carga un [PrivacyWorld] en los repositorios en memoria
 * que la vista usa, la invoca como [viewer] y traduce su respuesta (DTOs) a una [Exposure].
 */
interface ThirdPartyViewProbe {
    val name: String
    val context: ViewContext

    suspend fun expose(world: PrivacyWorld, viewer: UUID): Exposure
}

/**
 * Sondas de la suite.
 *
 * Cada clase de [me.santiagoreina.parcheu.api.graph.ThirdPartyViews.registered] necesita su sonda
 * en [byView] (fof E6.3, comunidades E6.2, presentaciones E6.4, sugerencias de inicio E4.7): el
 * test de registro falla si falta. [reference] se verifica siempre, haya o no vistas registradas.
 */
object ThirdPartyViewProbes {
    val byView: Map<KClass<out ThirdPartyView>, ThirdPartyViewProbe> = mapOf(
        HomeSuggestions::class to HomeSuggestionsProbe,
    )

    val reference: List<ThirdPartyViewProbe> = listOf(ThirdPartyNodesProbe, EgoGraphProbe)

    fun all(): List<ThirdPartyViewProbe> = reference + byView.values
}

private val signer = AnonIdSigner.forTests()

/**
 * "Nodos de terceros" en el peor caso: la vista toma como candidatos a **todo** el mundo y a
 * **todas** las aristas, y solo [GraphVisibilityPolicy] la separa de una fuga. Así se prueba que la
 * policy, por sí sola, basta como segunda barrera para cualquier vista de segundo grado (fof,
 * comunidades, presentaciones, sugerencias), aunque su SQL dejara pasar algo. Es el patrón que
 * deben seguir las vistas reales: candidatos → `canSee` → `canSeeEdge` → `present`.
 */
object ThirdPartyNodesProbe : ThirdPartyViewProbe {
    override val name = "nodos de terceros (solo la policy)"
    override val context = ViewContext.THIRD_PARTY

    private val policy = GraphVisibilityPolicy(signer)

    override suspend fun expose(world: PrivacyWorld, viewer: UUID): Exposure {
        val blocked = world.blockedEitherWay(viewer)
        val visible = world.people
            .filter { it.id != viewer && policy.canSee(viewer, it, ViewContext.THIRD_PARTY, blocked) }
            .associate { it.id to policy.present(viewer, it, world.connected(viewer, it.id), ViewContext.THIRD_PARTY).id }
        fun publicId(id: UUID): String? = if (id == viewer) viewer.toString() else visible[id]
        val edges = world.edges
            .filter { policy.canSeeEdge(viewer, it.a, it.b, it.mode) }
            .mapNotNull { edge ->
                val a = publicId(edge.a) ?: return@mapNotNull null
                val b = publicId(edge.b) ?: return@mapNotNull null
                ExposedEdge(a, b, edge.mode)
            }
        return Exposure(visible.values.toList(), edges)
    }
}

/**
 * Ego-grafo (`GET /v1/graph` y el detalle de cada nodo, E3.1) con el repositorio en memoria. No es
 * una vista de terceros (HIDDEN sí se ve en conexiones directas), pero comparte las reglas de
 * Pareja ajena, bloqueados y borrados.
 */
object EgoGraphProbe : ThirdPartyViewProbe {
    override val name = "ego-grafo"
    override val context = ViewContext.EGO

    override suspend fun expose(world: PrivacyWorld, viewer: UUID): Exposure {
        val service = GraphService(world.toGraphRepository(), GraphVisibilityPolicy(signer))
        val graph = service.graph(viewer, GraphFilter())
        val me = graph.me.id
        val details = graph.nodes.map { service.node(viewer, it.id) }
        return Exposure(
            personIds = graph.nodes.map { it.id } +
                details.mapNotNull { detail -> detail.lastSimulation?.other?.let { it.id ?: it.anonId } },
            edges = graph.edges.map { ExposedEdge(me, it.otherId, it.mode) } +
                details.flatMap { detail -> detail.sharedModes.map { ExposedEdge(me, detail.node.id, it) } },
        )
    }
}

/**
 * Sugerencias de Inicio (E4.7) con ParcheU+ (el caso que calcula amigos en común). Lo que se expone a
 * terceros son las aristas candidato–amigo que entran en `mutualCount`: se traducen a [ExposedEdge]
 * y se verifican en segundo grado (nunca Pareja ajena, HIDDEN, bloqueados ni borrados). Las personas
 * sugeridas son conexiones directas (contexto EGO) y se prueban en `HomeSuggestionsTest`.
 */
object HomeSuggestionsProbe : ThirdPartyViewProbe {
    override val name = "sugerencias de inicio (amigos en común)"
    override val context = ViewContext.THIRD_PARTY

    override suspend fun expose(world: PrivacyWorld, viewer: UUID): Exposure {
        val view = HomeSuggestions(world.toGraphRepository(), GraphVisibilityPolicy(signer)) { Mode.entries.toSet() }
        val edges = view.candidates(viewer, withMutuals = true)
            .flatMap { it.mutuals.orEmpty() }
            .map { ExposedEdge(it.candidateId, it.mutualId, it.mode) }
        return Exposure(personIds = edges.map { it.bId }, edges = edges)
    }
}
