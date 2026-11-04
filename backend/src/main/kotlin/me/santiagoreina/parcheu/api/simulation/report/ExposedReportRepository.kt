package me.santiagoreina.parcheu.api.simulation.report

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.santiagoreina.parcheu.api.platform.db.tables.ConnectionDecisions
import me.santiagoreina.parcheu.api.platform.db.tables.ReportUnlocks
import me.santiagoreina.parcheu.api.platform.db.tables.Simulations
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** `report_unlocks` y `connection_decisions` sobre Postgres. */
class ExposedReportRepository(private val db: Database) : ReportRepository {

    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(db) { block() } }

    override suspend fun isUnlocked(userId: UUID, simulationId: UUID): Boolean = tx {
        ReportUnlocks.selectAll()
            .where { (ReportUnlocks.userId eq userId) and (ReportUnlocks.simulationId eq simulationId) }
            .any()
    }

    override suspend fun recordUnlock(userId: UUID, simulationId: UUID, now: Instant) {
        tx {
            ReportUnlocks.insertIgnore {
                it[ReportUnlocks.userId] = userId
                it[ReportUnlocks.simulationId] = simulationId
                it[createdAt] = now.toOffset()
            }
        }
    }

    override suspend fun decisions(simulationId: UUID): Map<UUID, Boolean> = tx { readDecisions(simulationId) }

    override suspend fun decide(simulationId: UUID, userId: UUID, connect: Boolean, now: Instant): DecideOutcome = tx {
        // Serializa las decisiones de la misma simulación: la segunda espera a que la primera confirme
        // y entonces ve las dos (criterio de concurrencia de E4.6).
        Simulations.select(Simulations.id).where { Simulations.id eq simulationId }.forUpdate().singleOrNull()
            ?: error("La simulación $simulationId no existe")
        val before = readDecisions(simulationId)
        if (userId in before) return@tx DecideOutcome.AlreadyDecided
        ConnectionDecisions.insert {
            it[ConnectionDecisions.simulationId] = simulationId
            it[ConnectionDecisions.userId] = userId
            it[ConnectionDecisions.connect] = connect
            it[decidedAt] = now.toOffset()
        }
        DecideOutcome.Saved(before + (userId to connect))
    }

    private fun readDecisions(simulationId: UUID): Map<UUID, Boolean> =
        ConnectionDecisions.selectAll()
            .where { ConnectionDecisions.simulationId eq simulationId }
            .associate { it[ConnectionDecisions.userId] to it[ConnectionDecisions.connect] }
}

private fun Instant.toOffset(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)
