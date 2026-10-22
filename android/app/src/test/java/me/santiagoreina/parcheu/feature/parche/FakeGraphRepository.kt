package me.santiagoreina.parcheu.feature.parche

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.GraphDto
import me.santiagoreina.parcheu.contract.GraphStats
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NodeDetail
import me.santiagoreina.parcheu.contract.NodeDto
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.SimulationSummary
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

/** Datos de D1 del mockup 08a: los mismos 9 nodos de PU Grafo. */
object ParcheSamples {
    private fun node(
        id: String,
        label: String,
        affinity: Int,
        mode: Mode,
        state: EdgeState,
        anonymous: Boolean = false
    ) = NodeDto(id, label, label.take(1), null, affinity, mode, state, anonymous)

    val me = node("me", "Tú", 100, Mode.AMISTAD, EdgeState.CONNECTED)

    val nodes =
        listOf(
            node("val", "Valentina", 92, Mode.AMISTAD, EdgeState.CONNECTED),
            node("and", "Andrés", 88, Mode.ESTUDIO, EdgeState.CONNECTED),
            node("seb", "Sebas", 76, Mode.AMISTAD, EdgeState.CONNECTED),
            node("cam", "Camila", 85, Mode.PAREJA, EdgeState.CONNECTED),
            node("lau", "Laura P.", 87, Mode.ESTUDIO, EdgeState.CONNECTED),
            node("juli", "Juli", 62, Mode.AMISTAD, EdgeState.CONNECTED),
            node("mat", "Mateo", 58, Mode.AMISTAD, EdgeState.PENDING),
            node("dan", "Daniela", 54, Mode.ESTUDIO, EdgeState.SIMULATED),
            node("nico", "Nico", 50, Mode.AMISTAD, EdgeState.PENDING, anonymous = true)
        )

    val graph =
        GraphDto(
            me,
            nodes,
            emptyList(),
            GraphStats(connections = 9, simulated = 1, pending = 1, avgAffinity = 75)
        )

    val content = ParcheContent(graph, plus = false)

    val empty = ParcheContent(graph.copy(nodes = emptyList()), plus = false)

    val createdAt = Instant.parse("2026-11-01T10:00:00Z")

    fun summary(id: String = "sim-1", other: NodeDto = nodes[1]) = SimulationSummary(
        id = id,
        status = SimulationStatus.DONE,
        mode = other.mode,
        other = PersonRef(id = other.id, label = other.label, initials = other.initials),
        createdAt = createdAt,
        origin = SimulationOrigin.PARCHE,
        score = other.affinity
    )

    fun detail(node: NodeDto, withSimulation: Boolean = true) = NodeDetail(
        node = node,
        lastSimulation = if (withSimulation) summary(other = node) else null,
        sharedModes = listOf(node.mode)
    )
}

/** [GraphRepository] en memoria. [graphResults] se consumen en orden y la última se repite. */
class FakeGraphRepository(var cachedContent: ParcheContent? = null) : GraphRepository {
    val graphResults = ArrayDeque<ApiResult<ParcheContent>>()
    var detailResult: (String) -> ApiResult<NodeDetail> = { id ->
        ApiResult.Success(ParcheSamples.detail(ParcheSamples.nodes.first { it.id == id }))
    }
    var startResult: ApiResult<SimulationSummary> = ApiResult.Success(
        ParcheSamples.summary("sim-new")
    )

    val graphCalls = mutableListOf<String?>()
    val startedWith = mutableListOf<Pair<String, Mode>>()

    fun graphReturns(vararg results: ApiResult<ParcheContent>) {
        graphResults.clear()
        graphResults.addAll(results)
    }

    override suspend fun cached(): ParcheContent? = cachedContent

    override suspend fun graph(community: String?): ApiResult<ParcheContent> {
        graphCalls += community
        return if (graphResults.size > 1) graphResults.removeFirst() else graphResults.first()
    }

    override suspend fun nodeDetail(id: String): ApiResult<NodeDetail> = detailResult(id)

    override suspend fun startSimulation(
        otherUserId: String,
        mode: Mode
    ): ApiResult<SimulationSummary> {
        startedWith += otherUserId to mode
        return startResult
    }
}

fun failure(error: AppError = AppError.Network) = ApiResult.Failure(error)
