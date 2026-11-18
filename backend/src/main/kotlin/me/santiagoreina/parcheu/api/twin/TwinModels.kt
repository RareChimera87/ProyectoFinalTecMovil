package me.santiagoreina.parcheu.api.twin

import me.santiagoreina.parcheu.contract.TwinAnswer
import java.time.LocalDate
import java.util.UUID

/** Número exacto de respuestas que exige `POST /v1/twin/answers`. */
const val TWIN_QUESTION_COUNT = 12

/** XP por pregunta respondida la primera vez (arquitectura.md §7.1, punto 6). */
const val XP_PER_ANSWER = 10

/*
 * XP por actividad (arquitectura.md §7.1, punto 6). No se guarda: se cuenta en cada lectura desde filas que ya
 * existen y tienen clave única, así que un evento nunca suma dos veces y las cuentas anteriores reciben
 * exactamente el XP de lo que ya hicieron.
 */

/** Por memoria aceptada (`twin_memories.status = ACCEPTED`, E9.2). */
const val XP_PER_ACCEPTED_MEMORY = 10

/** Por respuesta del gemelo calificada (`twin_feedback` RATING, una por turno, E9.3). */
const val XP_PER_RATING = 5

/** Por pregunta de control semanal respondida (`twin_feedback` CONTROL con `actual`, E9.3). */
const val XP_PER_CONTROL_ANSWER = 10

/** Por simulación propia terminada: la pidió la persona, participa su gemelo y quedó DONE (§7.2). */
const val XP_PER_DONE_SIMULATION = 20

/** Conteo de los eventos que dan XP además de las 12 respuestas. */
data class TwinActivity(
    val acceptedMemories: Int = 0,
    val ratings: Int = 0,
    val controlAnswers: Int = 0,
    val doneSimulations: Int = 0,
) {
    val xp: Int
        get() = acceptedMemories * XP_PER_ACCEPTED_MEMORY + ratings * XP_PER_RATING +
            controlAnswers * XP_PER_CONTROL_ANSWER + doneSimulations * XP_PER_DONE_SIMULATION

    companion object {
        val NONE = TwinActivity()
    }
}

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
) {
    /** Suma el XP de [activity] al de las respuestas (`twins.xp`) y recalcula el nivel. */
    fun withActivity(activity: TwinActivity): TwinState = (xp + activity.xp).let { total -> copy(xp = total, level = levelFor(total)) }
}

/** Una respuesta del usuario junto con su pregunta, para armar la persona. */
data class AnsweredQuestion(val question: WeightedQuestion, val answer: TwinAnswer)

/** Lo único que lee `PersonaBuilder`: las 12 respuestas. Nunca datos del usuario ni memorias. */
data class PersonaInput(
    val version: Int,
    val personaReady: Boolean,
    val traits: List<Double>,
    val answers: List<AnsweredQuestion>,
)

/** Punto semanal de `twin_accuracy`; [ratings] y [predictions] son los conteos que lo produjeron. */
data class AccuracyPoint(val week: LocalDate, val accuracy: Int, val ratings: Int = 0, val predictions: Int = 0)

/** Resultado de guardar las respuestas. */
data class SavedAnswers(val state: TwinState, val version: Int)

/** Cuerpo de las tareas de `/internal/tasks`: `{"id": "<uuid>"}`. */
@kotlinx.serialization.Serializable
data class TaskPayload(val id: String) {
    fun uuid(): UUID = runCatching { UUID.fromString(id) }.getOrNull()
        ?: throw me.santiagoreina.parcheu.api.platform.errors.ValidationFailed("id no es un UUID")
}
