package me.santiagoreina.parcheu.api.twin.feedback

import me.santiagoreina.parcheu.contract.TwinAnswer
import java.time.Instant
import java.util.UUID

/** Repositorio en memoria para pruebas y `ENV=local` sin base. Los turnos se siembran con [addTurn]. */
class InMemoryFeedbackRepository : FeedbackRepository {
    private data class Turn(val userId: UUID, val simulationId: UUID, val index: Int, val text: String, val order: Long)
    private data class Rating(val userId: UUID, val simulationId: UUID, val turnIndex: Int, val rating: Int, val at: Instant)

    private val lock = Any()
    private val turns = mutableListOf<Turn>()
    private val ratings = mutableListOf<Rating>()
    private val controls = mutableListOf<ControlRecord>()
    private var seq = 0L

    /** Siembra un turno del gemelo de [userId]; las simulaciones sembradas después son "más recientes". */
    fun addTurn(userId: UUID, simulationId: UUID, turnIndex: Int, text: String) = synchronized(lock) {
        turns += Turn(userId, simulationId, turnIndex, text, seq++)
    }

    override fun ratableTurns(userId: UUID, limit: Int): List<RatableTurn> = synchronized(lock) {
        turns.filter { t -> t.userId == userId && ratings.none { it.userId == userId && it.simulationId == t.simulationId && it.turnIndex == t.index } }
            .sortedWith(compareByDescending<Turn> { it.order }.thenBy { it.index })
            .take(limit)
            .map { RatableTurn(it.simulationId, it.index, it.text) }
    }

    override fun ownTurnText(userId: UUID, simulationId: UUID, turnIndex: Int): String? = synchronized(lock) {
        turns.firstOrNull { it.userId == userId && it.simulationId == simulationId && it.index == turnIndex }?.text
    }

    override fun addRating(userId: UUID, simulationId: UUID, turnIndex: Int, rating: Int, now: Instant): Boolean = synchronized(lock) {
        if (ratings.any { it.userId == userId && it.simulationId == simulationId && it.turnIndex == turnIndex }) return false
        ratings += Rating(userId, simulationId, turnIndex, rating, now)
        true
    }

    override fun controls(userId: UUID): List<ControlRecord> = synchronized(lock) {
        controls.filter { it.userId == userId }.sortedBy { it.createdAt }
    }

    override fun createControl(userId: UUID, questionId: Int, predicted: TwinAnswer, now: Instant): ControlRecord = synchronized(lock) {
        ControlRecord(UUID.randomUUID(), userId, questionId, predicted, null, now).also { controls += it }
    }

    override fun answerControl(userId: UUID, id: UUID, actual: TwinAnswer): ControlAnswer = synchronized(lock) {
        val i = controls.indexOfFirst { it.id == id && it.userId == userId }
        if (i < 0) return ControlAnswer.NotFound
        val current = controls[i]
        if (current.actual != null) return ControlAnswer.AlreadyAnswered(current)
        controls[i] = current.copy(actual = actual)
        ControlAnswer.Saved(controls[i])
    }

    override fun weekStats(userId: UUID, from: Instant, to: Instant): WeekStats = synchronized(lock) {
        val rs = ratings.filter { it.userId == userId && it.at >= from && it.at < to }
        val cs = controls.filter { it.userId == userId && it.actual != null && it.createdAt >= from && it.createdAt < to }
        WeekStats(rs.size, rs.count { it.rating == RATING_POSITIVE }, cs.size, cs.count { it.actual == it.predicted })
    }

    override fun usersWithData(from: Instant, to: Instant): List<UUID> = synchronized(lock) {
        val fromRatings = ratings.filter { it.at >= from && it.at < to }.map { it.userId }
        val fromControls = controls.filter { it.actual != null && it.createdAt >= from && it.createdAt < to }.map { it.userId }
        (fromRatings + fromControls).distinct()
    }
}
