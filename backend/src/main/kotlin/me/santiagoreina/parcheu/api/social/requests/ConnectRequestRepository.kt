package me.santiagoreina.parcheu.api.social.requests

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.platform.db.tables.ConnectionDecisions
import me.santiagoreina.parcheu.api.platform.db.tables.Simulations
import me.santiagoreina.parcheu.api.simulation.SimulationCursor
import me.santiagoreina.parcheu.api.simulation.SimulationRepository
import me.santiagoreina.parcheu.api.simulation.report.ReportRepository
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.util.UUID

/**
 * Una decisión `connect = true` de [otherId] sobre una simulación terminada con el observador, a la que
 * el observador todavía no responde (backlog E6.6).
 */
data class PendingConnect(
    val simulationId: UUID,
    val otherId: UUID,
    val mode: Mode,
    val score: Int,
    val decidedAt: Instant,
)

/** Lectura de solicitudes de conexión pendientes. No escribe: decidir sigue siendo `POST .../decision` (E4.6). */
fun interface ConnectRequestRepository {
    /**
     * Decisiones de conectar hacia [viewer] que él aún no contesta (ni conectar ni 'Ahora no'), más
     * recientes primero, hasta [limit]. Solo simulaciones DONE que no sean de una presentación (esas no
     * admiten decisión). Los filtros de visibilidad no son de este repositorio: los aplica el servicio.
     */
    suspend fun pending(viewer: UUID, limit: Int): List<PendingConnect>
}

/** Postgres: `connection_decisions` unida a `simulations`. */
class ExposedConnectRequestRepository(private val db: Database) : ConnectRequestRepository {
    override suspend fun pending(viewer: UUID, limit: Int): List<PendingConnect> = withContext(Dispatchers.IO) {
        transaction(db) {
            val candidates = (ConnectionDecisions innerJoin Simulations)
                .selectAll()
                .where {
                    (ConnectionDecisions.connect eq true) and
                        (ConnectionDecisions.userId neq viewer) and
                        ((Simulations.userA eq viewer) or (Simulations.userB eq viewer)) and
                        (Simulations.status eq SimulationStatus.DONE) and
                        (Simulations.origin neq SimulationOrigin.INTRODUCTION)
                }
                .orderBy(ConnectionDecisions.decidedAt to SortOrder.DESC)
                .toList()
            if (candidates.isEmpty()) return@transaction emptyList()
            // Las que el observador ya contestó (conectar o 'Ahora no') salen de la lista.
            val answered = ConnectionDecisions
                .select(ConnectionDecisions.simulationId)
                .where {
                    (ConnectionDecisions.userId eq viewer) and
                        (ConnectionDecisions.simulationId inList candidates.map { it[ConnectionDecisions.simulationId] })
                }
                .map { it[ConnectionDecisions.simulationId] }
                .toSet()
            candidates
                .filter { it[ConnectionDecisions.simulationId] !in answered }
                .take(limit)
                .map {
                    PendingConnect(
                        simulationId = it[ConnectionDecisions.simulationId],
                        otherId = it[ConnectionDecisions.userId],
                        mode = it[Simulations.mode],
                        score = it[Simulations.score]?.toInt() ?: 0,
                        decidedAt = it[ConnectionDecisions.decidedAt].toInstant(),
                    )
                }
        }
    }
}

/**
 * Misma semántica sobre los repositorios en memoria de simulaciones y decisiones (pruebas y contenedor
 * por defecto): recorre las simulaciones del observador y mira las decisiones de cada una.
 */
class SimulationBackedConnectRequestRepository(
    private val simulations: SimulationRepository,
    private val reports: ReportRepository,
) : ConnectRequestRepository {
    override suspend fun pending(viewer: UUID, limit: Int): List<PendingConnect> {
        val all = buildList {
            var cursor: SimulationCursor? = null
            while (true) {
                val page = simulations.listFor(viewer, cursor, PAGE)
                addAll(page)
                if (page.size < PAGE) break
                cursor = page.last().let { SimulationCursor(it.createdAt, it.id) }
            }
        }
        return all
            .filter { it.status == SimulationStatus.DONE && it.origin != SimulationOrigin.INTRODUCTION }
            .mapNotNull { sim ->
                val other = sim.otherThan(viewer)
                val decisions = reports.decisions(sim.id)
                if (decisions[other] != true || viewer in decisions) return@mapNotNull null
                PendingConnect(sim.id, other, sim.mode, sim.score ?: 0, sim.finishedAt ?: sim.createdAt)
            }
            .sortedByDescending { it.decidedAt }
            .take(limit)
    }

    private companion object {
        const val PAGE = 100
    }
}
