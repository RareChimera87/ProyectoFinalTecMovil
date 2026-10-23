package me.santiagoreina.parcheu.contract

import kotlinx.serialization.Serializable

/* Ego-grafo del usuario (D1-D5, `GET /v1/graph` y `/v1/graph/nodes/{id}`). */

@Serializable
public enum class EdgeState { CONNECTED, SIMULATED, PENDING }

/** Nodo del grafo; `anonymous` = sin conexión mutua (sin nombre ni foto). `affinity` 0-100. */
@Serializable
public data class NodeDto(
    val id: String,
    val label: String,
    val initials: String,
    val photoUrl: String? = null,
    val affinity: Int,
    val mode: Mode,
    val state: EdgeState,
    val anonymous: Boolean = false,
)

@Serializable
public data class EdgeDto(
    val otherId: String,
    val mode: Mode,
    val state: EdgeState,
    val affinity: Int,
)

@Serializable
public data class GraphStats(
    val connections: Int,
    val simulated: Int,
    val pending: Int,
    val avgAffinity: Int,
)

/** GET /v1/graph?mode=&minAffinity=&community= */
@Serializable
public data class GraphDto(
    val me: NodeDto,
    val nodes: List<NodeDto> = emptyList(),
    val edges: List<EdgeDto> = emptyList(),
    val stats: GraphStats,
)

/** GET /v1/graph/nodes/{id} */
@Serializable
public data class NodeDetail(
    val node: NodeDto,
    val lastSimulation: SimulationSummary? = null,
    val sharedModes: List<Mode> = emptyList(),
)
