package me.santiagoreina.parcheu.api.twin.feedback

import me.santiagoreina.parcheu.contract.TwinAnswer
import java.time.Instant
import java.util.UUID

/** Persistencia de `twin_feedback` y las lecturas de `simulations` que necesita C4. */
interface FeedbackRepository {
    /**
     * Turnos del gemelo de [userId] en simulaciones DONE de origen RANDOM o PARCHE (no escenarios ni
     * presentaciones) que aún no tienen calificación, de la más reciente a la más antigua.
     * `turnIndex` es el índice plano sobre los turnos de todos los escenarios (el mismo de E8.7).
     */
    fun ratableTurns(userId: UUID, limit: Int): List<RatableTurn>

    /** Texto del turno si es un turno del gemelo de [userId] en una simulación suya; null si no. */
    fun ownTurnText(userId: UUID, simulationId: UUID, turnIndex: Int): String?

    /** Guarda la calificación. Devuelve false si ese turno ya estaba calificado (no duplica). */
    fun addRating(userId: UUID, simulationId: UUID, turnIndex: Int, rating: Int, now: Instant): Boolean

    /** Preguntas de control del usuario, de la más antigua a la más reciente. */
    fun controls(userId: UUID): List<ControlRecord>

    /** Guarda la predicción **antes** de que el usuario vea la pregunta. */
    fun createControl(userId: UUID, questionId: Int, predicted: TwinAnswer, now: Instant): ControlRecord

    /** Fija `actual` solo si estaba vacío. */
    fun answerControl(userId: UUID, id: UUID, actual: TwinAnswer): ControlAnswer

    /** Conteos del usuario con filas creadas en `[from, to)`. */
    fun weekStats(userId: UUID, from: Instant, to: Instant): WeekStats

    /** Usuarios con alguna calificación o control respondido creado en `[from, to)`. */
    fun usersWithData(from: Instant, to: Instant): List<UUID>
}
