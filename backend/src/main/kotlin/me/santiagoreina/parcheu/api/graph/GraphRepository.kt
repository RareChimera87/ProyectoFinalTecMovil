package me.santiagoreina.parcheu.api.graph

import me.santiagoreina.parcheu.contract.Mode
import java.time.Instant
import java.util.UUID

/**
 * Acceso a datos del grafo. La implementación real es [ExposedGraphRepository]; [InMemoryGraphRepository]
 * replica su semántica para desarrollo local y pruebas de rutas.
 *
 * Las lecturas del ego-grafo ya excluyen en SQL a los usuarios borrados y a los bloqueados en
 * cualquier sentido (ADR-0012); [GraphService] vuelve a filtrar con [GraphVisibilityPolicy].
 */
interface GraphRepository {
    /** Aristas de [viewer] (todas, incluidas las de Pareja) con los filtros aplicados en SQL. */
    suspend fun egoEdges(viewer: UUID, filter: GraphFilter): List<EgoEdgeRow>

    /** Usuarios con los que [viewer] tiene alguna arista CONNECTED (en cualquier modo, sin filtros). */
    suspend fun connectedWith(viewer: UUID): Set<UUID>

    /** Usuarios bloqueados por [viewer] o que bloquearon a [viewer]. */
    suspend fun blockedEitherWay(viewer: UUID): Set<UUID>

    suspend fun person(userId: UUID): PersonRow?

    /** Primer instante en que [userId] entró a una comunidad (`communities.created_at` más antigua), o null. */
    suspend fun firstCommunityAt(userId: UUID): Instant? = null

    /**
     * Simulación más reciente entre los dos usuarios, en cualquier modo y estado. No cuenta las de una
     * presentación (`INTRODUCTION`, E6.2), que no se consultan por `/v1/simulations`.
     */
    suspend fun lastSimulation(a: UUID, b: UUID): SimulationRow?

    /**
     * Lee la arista `(a, b, mode)` bloqueándola, aplica [change] y guarda el resultado en una sola
     * transacción. No hace nada si hay un bloqueo entre los dos. Devuelve el estado final (o null
     * si no hay arista o hubo bloqueo).
     */
    suspend fun updateEdge(
        a: UUID,
        b: UUID,
        mode: Mode,
        now: Instant,
        change: (EdgeSnapshot?) -> EdgeChange,
    ): EdgeSnapshot?
}

/** Resultado de aplicar un evento a una arista. */
sealed interface EdgeChange {
    data object Keep : EdgeChange
    data object Remove : EdgeChange
    data class Save(val edge: EdgeSnapshot) : EdgeChange
}
