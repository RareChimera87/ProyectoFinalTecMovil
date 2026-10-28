package me.santiagoreina.parcheu.api.twin.feedback

import me.santiagoreina.parcheu.contract.TwinAnswer
import java.time.Instant
import java.util.UUID

/** Calificaciones de respuestas simuladas por pantalla de C4. */
const val RATING_PROMPTS = 3

/** Calificación positiva ("sí soy yo") y negativa ("no suena a mí"). */
const val RATING_POSITIVE = 1
const val RATING_NEGATIVE = 0

/** Un turno que el gemelo del usuario dijo en una simulación terminada y que aún no calificó. */
data class RatableTurn(val simulationId: UUID, val turnIndex: Int, val text: String)

/** Fila `kind = CONTROL` de `twin_feedback`: la predicción se guarda al crearla; `actual` llega después. */
data class ControlRecord(
    val id: UUID,
    val userId: UUID,
    val questionId: Int,
    val predicted: TwinAnswer,
    val actual: TwinAnswer?,
    val createdAt: Instant,
)

/** Conteos de una semana para la métrica 'Qué tanto te conoce'. */
data class WeekStats(val ratings: Int, val positive: Int, val predictions: Int, val correct: Int)

/** Resultado de decidir una respuesta de control. */
sealed interface ControlAnswer {
    data class Saved(val record: ControlRecord) : ControlAnswer
    data class AlreadyAnswered(val record: ControlRecord) : ControlAnswer
    data object NotFound : ControlAnswer
}
