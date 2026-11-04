package me.santiagoreina.parcheu.api.twin.feedback

import me.santiagoreina.parcheu.api.platform.db.tables.Simulations
import me.santiagoreina.parcheu.api.platform.db.tables.TwinFeedback
import me.santiagoreina.parcheu.api.simulation.Side
import me.santiagoreina.parcheu.api.simulation.StoredTranscript
import me.santiagoreina.parcheu.contract.ContractJson
import me.santiagoreina.parcheu.contract.FeedbackKind
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.TwinAnswer
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * `twin_feedback` en Postgres. La unicidad de una calificación por turno la garantiza el índice
 * parcial de `V20261027_1200__calificacion_unica_por_turno.sql`.
 */
class ExposedFeedbackRepository(private val db: Database) : FeedbackRepository {
    override fun ratableTurns(userId: UUID, limit: Int): List<RatableTurn> = transaction(db) {
        val rated = TwinFeedback.select(TwinFeedback.simulationId, TwinFeedback.turnIndex)
            .where { (TwinFeedback.userId eq userId) and (TwinFeedback.kind eq FeedbackKind.RATING) }
            .mapNotNull { row -> row[TwinFeedback.simulationId]?.let { it to row[TwinFeedback.turnIndex]?.toInt() } }
            .toSet()
        val out = mutableListOf<RatableTurn>()
        val sims = Simulations.selectAll()
            .where {
                ((Simulations.userA eq userId) or (Simulations.userB eq userId)) and
                    (Simulations.status eq SimulationStatus.DONE) and
                    (Simulations.origin inList RATABLE_ORIGINS)
            }
            .orderBy(Simulations.createdAt, SortOrder.DESC)
            .limit(SIMULATIONS_SCANNED)
        for (sim in sims) {
            val id = sim[Simulations.id]
            val mine = if (sim[Simulations.userA] == userId) Side.A else Side.B
            turnsOf(sim).forEachIndexed { index, turn ->
                if (turn.speaker == mine && (id to index) !in rated && out.size < limit) out += RatableTurn(id, index, turn.text)
            }
            if (out.size >= limit) break
        }
        out
    }

    override fun ownTurnText(userId: UUID, simulationId: UUID, turnIndex: Int): String? = transaction(db) {
        val sim = Simulations.selectAll()
            .where {
                (Simulations.id eq simulationId) and ((Simulations.userA eq userId) or (Simulations.userB eq userId)) and
                    (Simulations.status eq SimulationStatus.DONE) and (Simulations.origin inList RATABLE_ORIGINS)
            }
            .singleOrNull() ?: return@transaction null
        val mine = if (sim[Simulations.userA] == userId) Side.A else Side.B
        turnsOf(sim).getOrNull(turnIndex)?.takeIf { it.speaker == mine }?.text
    }

    override fun addRating(userId: UUID, simulationId: UUID, turnIndex: Int, rating: Int, now: Instant): Boolean = transaction(db) {
        TwinFeedback.insertIgnore {
            it[TwinFeedback.userId] = userId
            it[kind] = FeedbackKind.RATING
            it[TwinFeedback.simulationId] = simulationId
            it[TwinFeedback.turnIndex] = turnIndex.toShort()
            it[TwinFeedback.rating] = rating.toShort()
            it[createdAt] = now.toOffset()
        }.insertedCount > 0
    }

    override fun controls(userId: UUID): List<ControlRecord> = transaction(db) {
        TwinFeedback.selectAll()
            .where { (TwinFeedback.userId eq userId) and (TwinFeedback.kind eq FeedbackKind.CONTROL) }
            .orderBy(TwinFeedback.createdAt, SortOrder.ASC)
            .mapNotNull { it.toControl() }
    }

    override fun createControl(userId: UUID, questionId: Int, predicted: TwinAnswer, now: Instant): ControlRecord = transaction(db) {
        val row = TwinFeedback.insert {
            it[TwinFeedback.userId] = userId
            it[kind] = FeedbackKind.CONTROL
            it[TwinFeedback.questionId] = questionId.toShort()
            it[TwinFeedback.predicted] = predicted.name
            it[createdAt] = now.toOffset()
        }
        ControlRecord(row[TwinFeedback.id], userId, questionId, predicted, null, now)
    }

    override fun answerControl(userId: UUID, id: UUID, actual: TwinAnswer): ControlAnswer = transaction(db) {
        val row = TwinFeedback.selectAll()
            .where { (TwinFeedback.id eq id) and (TwinFeedback.userId eq userId) and (TwinFeedback.kind eq FeedbackKind.CONTROL) }
            .forUpdate()
            .singleOrNull()
        val record = row?.toControl() ?: return@transaction ControlAnswer.NotFound
        if (record.actual != null) return@transaction ControlAnswer.AlreadyAnswered(record)
        TwinFeedback.update({ TwinFeedback.id eq id }) { it[TwinFeedback.actual] = actual.name }
        ControlAnswer.Saved(record.copy(actual = actual))
    }

    override fun weekStats(userId: UUID, from: Instant, to: Instant): WeekStats = transaction(db) {
        val rows = TwinFeedback.selectAll()
            .where {
                (TwinFeedback.userId eq userId) and (TwinFeedback.createdAt greaterEq from.toOffset()) and
                    (TwinFeedback.createdAt less to.toOffset())
            }
            .toList()
        val ratings = rows.filter { it[TwinFeedback.kind] == FeedbackKind.RATING }
        val controls = rows.filter { it[TwinFeedback.kind] == FeedbackKind.CONTROL && it[TwinFeedback.actual] != null }
        WeekStats(
            ratings = ratings.size,
            positive = ratings.count { it[TwinFeedback.rating]?.toInt() == RATING_POSITIVE },
            predictions = controls.size,
            correct = controls.count { it[TwinFeedback.actual] == it[TwinFeedback.predicted] },
        )
    }

    override fun usersWithData(from: Instant, to: Instant): List<UUID> = transaction(db) {
        TwinFeedback.selectAll()
            .where { (TwinFeedback.createdAt greaterEq from.toOffset()) and (TwinFeedback.createdAt less to.toOffset()) }
            .filter { it[TwinFeedback.kind] == FeedbackKind.RATING || it[TwinFeedback.actual] != null }
            .map { it[TwinFeedback.userId] }
            .distinct()
    }

    private fun turnsOf(sim: ResultRow) =
        sim[Simulations.transcript]
            ?.let { ContractJson.decodeFromJsonElement(StoredTranscript.serializer(), it) }
            ?.scenarios?.flatMap { it.turns }
            .orEmpty()

    private fun ResultRow.toControl(): ControlRecord? {
        val question = this[TwinFeedback.questionId]?.toInt() ?: return null
        val predicted = this[TwinFeedback.predicted]?.let { runCatching { TwinAnswer.valueOf(it) }.getOrNull() } ?: return null
        val actual = this[TwinFeedback.actual]?.let { runCatching { TwinAnswer.valueOf(it) }.getOrNull() }
        return ControlRecord(this[TwinFeedback.id], this[TwinFeedback.userId], question, predicted, actual, this[TwinFeedback.createdAt].toInstant())
    }

    private companion object {
        val RATABLE_ORIGINS = listOf(SimulationOrigin.RANDOM, SimulationOrigin.PARCHE)

        /** Cuántas simulaciones recientes se revisan para armar la lista de por calificar. */
        const val SIMULATIONS_SCANNED = 10

        private fun Instant.toOffset(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)
    }
}
