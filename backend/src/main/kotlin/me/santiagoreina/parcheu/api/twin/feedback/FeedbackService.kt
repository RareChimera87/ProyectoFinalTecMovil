package me.santiagoreina.parcheu.api.twin.feedback

import me.santiagoreina.parcheu.api.platform.BogotaZone
import me.santiagoreina.parcheu.api.platform.errors.Conflict
import me.santiagoreina.parcheu.api.platform.errors.NotFound
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.api.twin.AccuracyPoint
import me.santiagoreina.parcheu.api.twin.TwinRepository
import me.santiagoreina.parcheu.api.twin.TwinService
import me.santiagoreina.parcheu.contract.FeedbackKind
import me.santiagoreina.parcheu.contract.Feedback
import me.santiagoreina.parcheu.contract.FeedbackPrompt
import me.santiagoreina.parcheu.contract.TwinAnswer
import me.santiagoreina.parcheu.contract.TwinDto
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * Entrenamiento por feedback (E9.3, arquitectura.md §7.1 punto 5).
 *
 * - `GET /v1/twin/feedback-prompts`: la pregunta de control de la semana (con la predicción del gemelo
 *   **ya guardada** antes de devolverla) y hasta [RATING_PROMPTS] respuestas simuladas por calificar.
 * - `POST /v1/twin/feedback`: guarda la calificación (`rating` 0 o 1) o la respuesta real (`answer`).
 * - Job `twin-accuracy` ([computeAccuracy]): un punto semanal por usuario con datos en `twin_accuracy`.
 *
 * Identificadores de prompt: RATING = `<simulationId>:<turnIndex>` (el mismo par que usa el reporte
 * AI_CONTENT de E8.7); CONTROL = el `id` de la fila de `twin_feedback`.
 */
class FeedbackService(
    private val repo: FeedbackRepository,
    private val twins: TwinRepository,
    private val twin: TwinService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(FeedbackService::class.java)

    fun prompts(userId: UUID): List<FeedbackPrompt> {
        val questions = twins.questions().associateBy { it.id }
        val control = currentControl(userId)?.let { record ->
            val q = questions[record.questionId] ?: return@let null
            FeedbackPrompt(
                id = record.id.toString(),
                kind = FeedbackKind.CONTROL,
                text = q.text,
                options = listOf(q.optionA, q.optionB, DEPENDS_LABEL),
            )
        }
        val ratings = repo.ratableTurns(userId, RATING_PROMPTS).map {
            FeedbackPrompt(id = "${it.simulationId}:${it.turnIndex}", kind = FeedbackKind.RATING, text = it.text)
        }
        return listOfNotNull(control) + ratings
    }

    /**
     * La pregunta de control sin responder de esta semana. Si la semana aún no tiene ninguna se crea
     * una (la menos reciente del ciclo de 12) guardando primero la predicción; si ya se respondió, no hay.
     */
    private fun currentControl(userId: UUID): ControlRecord? {
        val weekStart = weekBounds(clock.instant()).first
        val controls = repo.controls(userId)
        controls.lastOrNull { it.createdAt >= weekStart }?.let { return it.takeIf { c -> c.actual == null } }
        val input = twins.personaInput(userId) ?: return null
        val questions = input.answers.map { it.question }.sortedBy { it.ord }
        if (questions.isEmpty()) return null
        val target = questions[controls.size % questions.size]
        val answers = input.answers.associate { it.question.id to it.answer }
        val predicted = ControlPredictor.predict(twins.questions(), answers, target)
        return repo.createControl(userId, target.id, predicted, clock.instant())
    }

    suspend fun submit(userId: UUID, body: Feedback): TwinDto {
        if (':' in body.promptId) submitRating(userId, body) else submitControl(userId, body)
        return twin.twin(userId)
    }

    private fun submitRating(userId: UUID, body: Feedback) {
        val (sim, turn) = body.promptId.split(':', limit = 2).let { parts ->
            val sim = runCatching { UUID.fromString(parts[0]) }.getOrNull()
            val turn = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 0 }
            if (sim == null || turn == null) throw ValidationFailed("promptId inválido")
            sim to turn
        }
        val rating = body.rating ?: throw ValidationFailed("Falta rating")
        if (rating != RATING_POSITIVE && rating != RATING_NEGATIVE) throw ValidationFailed("rating debe ser 0 o 1")
        repo.ownTurnText(userId, sim, turn) ?: throw NotFound("Respuesta no encontrada")
        // Repetir la misma calificación no hace nada: el índice único evita duplicados y sesgos.
        repo.addRating(userId, sim, turn, rating, clock.instant())
    }

    private fun submitControl(userId: UUID, body: Feedback) {
        val id = runCatching { UUID.fromString(body.promptId) }.getOrNull() ?: throw ValidationFailed("promptId inválido")
        val raw = body.answer ?: throw ValidationFailed("Falta answer")
        val answer = TwinAnswer.entries.firstOrNull { it.name == raw.uppercase() } ?: throw ValidationFailed("answer debe ser A, B o DEPENDS")
        when (val result = repo.answerControl(userId, id, answer)) {
            ControlAnswer.NotFound -> throw NotFound("Pregunta no encontrada")
            is ControlAnswer.AlreadyAnswered ->
                if (result.record.actual != answer) throw Conflict("Esa pregunta ya fue respondida")
            is ControlAnswer.Saved -> Unit
        }
    }

    /**
     * Handler de `POST /internal/jobs/twin-accuracy` (domingos 23:00 Bogotá): calcula la semana (lunes a
     * domingo, Bogotá) que contiene el instante actual y guarda un punto por usuario con datos. Es
     * idempotente (upsert por `(user_id, week)`): correrlo dos veces no duplica. Devuelve cuántos puntos escribió.
     */
    fun computeAccuracy(): Int {
        val (from, to) = weekBounds(clock.instant())
        val week = LocalDate.ofInstant(from, BogotaZone)
        var written = 0
        for (userId in repo.usersWithData(from, to)) {
            val stats = repo.weekStats(userId, from, to)
            val accuracy = AccuracyMetric.stored(stats) ?: continue
            twins.addAccuracyPoint(userId, AccuracyPoint(week, accuracy, stats.ratings, stats.predictions))
            written++
        }
        log.info("Job twin-accuracy: {} puntos para la semana {}", written, week)
        return written
    }

    /** `[lunes 00:00, lunes siguiente 00:00)` de Bogotá de la semana que contiene [now]. */
    private fun weekBounds(now: Instant): Pair<Instant, Instant> {
        val monday = LocalDate.ofInstant(now, BogotaZone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return monday.atStartOfDay(BogotaZone).toInstant() to monday.plusWeeks(1).atStartOfDay(BogotaZone).toInstant()
    }

    companion object {
        const val JOB_PATH = "/jobs/twin-accuracy"
        const val DEPENDS_LABEL = "Depende"
    }
}
