package me.santiagoreina.parcheu.contract

import kotlinx.serialization.Serializable

/* Ego-grafo del usuario (D1-D5, `GET /v1/graph` y `/v1/graph/nodes/{id}`). */

@Serializable
public enum class EdgeState { CONNECTED, SIMULATED, PENDING }

/**
 * Nodo del grafo; `anonymous` = sin conexión mutua (sin nombre ni foto). `isDemo` marca un perfil de
 * demostración (ADR-0027): la UI muestra la insignia 'Perfil de demostración'.
 *
 * Afinidad: `affinity` va de 0 a 100 y `affinityMeasured = false` dice que aún no se ha medido (por ejemplo,
 * una conexión por invitación sin simulación). Entonces `affinity` vale 0 solo por compatibilidad con las
 * apps 0.1/0.2, que exigen el número; el valor real es [measuredAffinity] (`null` = 'Sin simular'). Un
 * backend anterior no envía `affinityMeasured` y el valor por defecto `true` conserva su comportamiento.
 */
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
    val isDemo: Boolean = false,
    val affinityMeasured: Boolean = true,
) {
    /** Afinidad 0-100 si se midió; `null` = sin simular. Es lo que deben mostrar y filtrar los clientes. */
    public val measuredAffinity: Int? get() = affinity.takeIf { affinityMeasured }
}

/** Arista del ego-grafo; `affinity`/`affinityMeasured` siguen la misma regla que en [NodeDto]. */
@Serializable
public data class EdgeDto(
    val otherId: String,
    val mode: Mode,
    val state: EdgeState,
    val affinity: Int,
    val affinityMeasured: Boolean = true,
) {
    /** Afinidad 0-100 si se midió; `null` = sin simular. */
    public val measuredAffinity: Int? get() = affinity.takeIf { affinityMeasured }
}

/** `avgAffinity` promedia solo afinidades medidas (0 si no hay ninguna). */
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
    /** Id del chat con este nodo cuando la conexión es `CONNECTED`; `null` si no hay chat. */
    val chatId: String? = null,
)
