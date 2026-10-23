package me.santiagoreina.parcheu.api.twin

import me.santiagoreina.parcheu.contract.TwinAnswer
import java.time.LocalDate
import java.util.UUID

/** Número exacto de respuestas que exige `POST /v1/twin/answers`. */
const val TWIN_QUESTION_COUNT = 12

/** XP por pregunta respondida la primera vez (arquitectura.md §7.1, punto 6). */
const val XP_PER_ANSWER = 10

/** Un nivel cada 500 XP; el nivel 1 empieza en 0. */
const val XP_PER_LEVEL = 500

fun levelFor(xp: Int): Int = xp / XP_PER_LEVEL + 1

/** XP total (acumulado) con el que se alcanza el siguiente nivel. */
fun xpNextFor(xp: Int): Int = levelFor(xp) * XP_PER_LEVEL

/** Fila de `twins` más lo necesario para armar `TwinDto`. */
data class TwinState(
    val traits: List<Double>?,
    val personaReady: Boolean,
    val version: Int,
    val level: Int,
    val xp: Int,
    /** Precisión (0-100) de la semana más reciente, si hay datos. */
    val accuracy: Int?,
)

/** Una respuesta del usuario junto con su pregunta, para armar la persona. */
data class AnsweredQuestion(val question: WeightedQuestion, val answer: TwinAnswer)

/** Lo único que lee `PersonaBuilder`: las 12 respuestas. Nunca datos del usuario ni memorias. */
data class PersonaInput(
    val version: Int,
    val personaReady: Boolean,
    val traits: List<Double>,
    val answers: List<AnsweredQuestion>,
)

data class AccuracyPoint(val week: LocalDate, val accuracy: Int)

/** Resultado de guardar las respuestas. */
data class SavedAnswers(val state: TwinState, val version: Int)

/** Cuerpo de las tareas de `/internal/tasks`: `{"id": "<uuid>"}`. */
@kotlinx.serialization.Serializable
data class TaskPayload(val id: String) {
    fun uuid(): UUID = runCatching { UUID.fromString(id) }.getOrNull()
        ?: throw me.santiagoreina.parcheu.api.platform.errors.ValidationFailed("id no es un UUID")
}
