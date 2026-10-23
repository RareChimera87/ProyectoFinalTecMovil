package me.santiagoreina.parcheu.api.graph

import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Origen de un cambio de arista (para trazas): simulaciones, decisiones, invitaciones y presentaciones. */
enum class EdgeSource { SIMULATION, DECISION, INVITE, INTRODUCTION }

/** Hecho del dominio que da de alta o actualiza la arista entre [a] y [b] en [mode]. */
sealed interface EdgeEvent {
    val a: UUID
    val b: UUID
    val mode: Mode
    val source: EdgeSource

    /** Terminó una simulación aleatoria o del parche con su afinidad 0-100 (la de una presentación no crea arista, E6.2). */
    data class Simulated(
        override val a: UUID,
        override val b: UUID,
        override val mode: Mode,
        val simulationId: UUID,
        val affinity: Int,
        override val source: EdgeSource = EdgeSource.SIMULATION,
    ) : EdgeEvent

    /** Una de las partes quiere conectar y falta la otra (decisión unilateral o invitación sin aceptar). */
    data class InterestShown(
        override val a: UUID,
        override val b: UUID,
        override val mode: Mode,
        override val source: EdgeSource,
    ) : EdgeEvent

    /** Las dos partes quieren conectar (decisión mutua, invitación aceptada, presentación aceptada). */
    data class Connected(
        override val a: UUID,
        override val b: UUID,
        override val mode: Mode,
        override val source: EdgeSource,
    ) : EdgeEvent

    /** Una parte rechazó: lo pendiente vuelve a SIMULATED (o desaparece si nunca hubo simulación). */
    data class Declined(
        override val a: UUID,
        override val b: UUID,
        override val mode: Mode,
        override val source: EdgeSource,
    ) : EdgeEvent
}

/**
 * Reglas de transición de una arista, puras y sin E/S. Una arista CONNECTED nunca baja de estado
 * por estos eventos (desconectar es otra feature); la afinidad y la última simulación sí se actualizan.
 */
object EdgeTransitions {
    fun next(current: EdgeSnapshot?, event: EdgeEvent, now: Instant): EdgeChange = when (event) {
        is EdgeEvent.Simulated -> {
            require(event.affinity in 0..100) { "La afinidad va de 0 a 100: ${event.affinity}" }
            EdgeChange.Save(
                EdgeSnapshot(
                    state = current?.state ?: EdgeState.SIMULATED,
                    affinity = event.affinity,
                    lastSimulationId = event.simulationId,
                    createdAt = current?.createdAt ?: now,
                    connectedAt = current?.connectedAt,
                ),
            )
        }
        is EdgeEvent.InterestShown -> when (current?.state) {
            EdgeState.CONNECTED, EdgeState.PENDING -> EdgeChange.Keep
            EdgeState.SIMULATED -> EdgeChange.Save(current.copy(state = EdgeState.PENDING))
            null -> EdgeChange.Save(EdgeSnapshot(EdgeState.PENDING, null, null, now, null))
        }
        is EdgeEvent.Connected -> when (current?.state) {
            EdgeState.CONNECTED -> EdgeChange.Keep
            null -> EdgeChange.Save(EdgeSnapshot(EdgeState.CONNECTED, null, null, now, now))
            else -> EdgeChange.Save(current.copy(state = EdgeState.CONNECTED, connectedAt = now))
        }
        is EdgeEvent.Declined -> when (current?.state) {
            EdgeState.PENDING ->
                if (current.lastSimulationId != null) EdgeChange.Save(current.copy(state = EdgeState.SIMULATED)) else EdgeChange.Remove
            else -> EdgeChange.Keep
        }
    }
}

/**
 * Alta y actualización de aristas (E3.1). Lo llaman la simulación (E4.3), la decisión mutua (E4.5),
 * las invitaciones (E6.1) y las presentaciones (E6.4); nunca las rutas del grafo. Ignora los eventos
 * entre usuarios con un bloqueo en cualquier sentido.
 */
class EdgeService(private val repo: GraphRepository, private val clock: Clock) {
    private val log = LoggerFactory.getLogger(EdgeService::class.java)

    /** Aplica [event] y devuelve el estado final de la arista (null si no existe o hubo bloqueo). */
    suspend fun apply(event: EdgeEvent): EdgeSnapshot? {
        require(event.a != event.b) { "Una arista necesita dos usuarios distintos" }
        val now = clock.instant()
        val result = repo.updateEdge(event.a, event.b, event.mode, now) { current -> EdgeTransitions.next(current, event, now) }
        log.info("Arista {} {} desde {}: {}", event.mode, event::class.simpleName, event.source, result?.state ?: "sin arista")
        return result
    }
}
