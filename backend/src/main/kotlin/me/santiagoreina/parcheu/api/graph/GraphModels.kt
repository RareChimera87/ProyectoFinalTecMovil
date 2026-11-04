package me.santiagoreina.parcheu.api.graph

import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.Visibility
import java.time.Instant
import java.util.UUID

/** Datos de un usuario que el grafo necesita para presentarlo (sin tipos de Exposed). */
data class PersonRow(
    val id: UUID,
    val displayName: String?,
    val photoUrl: String?,
    /** `universities.short_name` (p. ej. "UIS"); nulo si no la ha elegido. */
    val universityShort: String?,
    val visibility: Visibility,
    val isDemo: Boolean,
    val deletedAt: Instant?,
)

/** Una arista vista desde el observador (`edges_v` con `me` = observador) y el otro extremo. */
data class EgoEdgeRow(
    val other: PersonRow,
    val mode: Mode,
    val state: EdgeState,
    val affinity: Int?,
    val lastSimulationId: UUID?,
    val connectedAt: Instant?,
    /** `edges.created_at`: cuándo nació la arista (E6.5 la usa para el historial). */
    val createdAt: Instant? = null,
)

/** Filtros de `GET /v1/graph`: `mode`, `minAffinity` (0-100) y `community` (id de comunidad). */
data class GraphFilter(
    val mode: Mode? = null,
    val minAffinity: Int? = null,
    val community: UUID? = null,
)

/** Estado guardado de una arista (una fila de `edges`). */
data class EdgeSnapshot(
    val state: EdgeState,
    val affinity: Int?,
    val lastSimulationId: UUID?,
    val createdAt: Instant,
    val connectedAt: Instant?,
)

/** Última simulación entre el observador y otra persona (para `NodeDetail.lastSimulation`). */
data class SimulationRow(
    val id: UUID,
    val status: SimulationStatus,
    val mode: Mode,
    val origin: SimulationOrigin,
    val score: Int?,
    val createdAt: Instant,
)
