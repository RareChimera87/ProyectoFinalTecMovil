package me.santiagoreina.parcheu.api.twin

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.santiagoreina.parcheu.api.platform.errors.ValidationFailed
import me.santiagoreina.parcheu.api.platform.tasks.QueueKind
import me.santiagoreina.parcheu.api.platform.tasks.TaskQueue
import me.santiagoreina.parcheu.api.platform.tasks.TaskRequest
import me.santiagoreina.parcheu.contract.Answers
import me.santiagoreina.parcheu.contract.QuestionDto
import me.santiagoreina.parcheu.contract.Trait
import me.santiagoreina.parcheu.contract.TraitKey
import me.santiagoreina.parcheu.contract.TwinDto
import me.santiagoreina.parcheu.contract.TwinPoint
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

/** Lógica del gemelo (arquitectura.md §7.1): respuestas, rasgos, persona asíncrona y evolución. */
class TwinService(
    private val repo: TwinRepository,
    private val persona: PersonaBuilder,
    private val tasks: TaskQueue,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(TwinService::class.java)

    fun questions(): List<QuestionDto> =
        repo.questions().map { QuestionDto(it.id, it.text, it.optionA, it.optionB) }

    /**
     * Valida exactamente 12 respuestas (una por pregunta), calcula los rasgos, guarda y encola la
     * construcción de la persona. Si el encolado falla las respuestas igual quedan guardadas
     * (`personaReady = false`) y reenviarlas reintenta la tarea.
     */
    suspend fun submitAnswers(userId: UUID, body: Answers): TwinDto {
        val questions = repo.questions()
        val ids = body.items.map { it.questionId }
        if (body.items.size != TWIN_QUESTION_COUNT) {
            throw ValidationFailed("Se requieren exactamente $TWIN_QUESTION_COUNT respuestas y llegaron ${body.items.size}")
        }
        if (ids.toSet().size != ids.size) throw ValidationFailed("Hay preguntas respondidas más de una vez")
        val known = questions.map { it.id }.toSet()
        ids.firstOrNull { it !in known }?.let { throw ValidationFailed("La pregunta $it no existe") }
        if (known.size != TWIN_QUESTION_COUNT) error("El catálogo tiene ${known.size} preguntas y se esperaban $TWIN_QUESTION_COUNT")

        val answers = body.items.associate { it.questionId to it.answer }
        val traits = TraitEngine.compute(questions, answers)
        val saved = repo.saveAnswers(userId, answers, traits, clock.instant())
        try {
            tasks.enqueue(buildPersonaTask(userId, saved.version))
        } catch (e: Exception) {
            log.error("No se pudo encolar build-persona para el usuario {} (versión {})", userId, saved.version, e)
        }
        return saved.state.toDto()
    }

    /** Estado del gemelo; sin respuestas devuelve rasgos vacíos, nivel 1 y `personaReady = false`. */
    fun twin(userId: UUID): TwinDto = (repo.state(userId) ?: EMPTY_STATE).toDto()

    /**
     * Un punto por cada semana con datos en `twin_accuracy`, por semana ascendente. `month` es el mes
     * (`yyyy-MM`) de esa semana y `level` el nivel actual (no se guarda historial de niveles).
     */
    fun evolution(userId: UUID): List<TwinPoint> {
        val points = repo.accuracyPoints(userId)
        if (points.isEmpty()) return emptyList()
        val level = repo.state(userId)?.level ?: 1
        return points.sortedBy { it.week }.map { TwinPoint("%04d-%02d".format(it.week.year, it.week.monthValue), it.accuracy, level) }
    }

    /**
     * Handler de `/internal/tasks/build-persona`. Idempotente: si la versión ya tiene persona no
     * vuelve a llamar al modelo, y `twin_versions` recibe a lo sumo una fila por versión. Sin
     * respuestas no hay nada que hacer (204, sin reintento). Si el modelo falla propaga el error
     * para que Cloud Tasks reintente.
     */
    suspend fun buildPersona(userId: UUID) {
        val input = repo.personaInput(userId)
        if (input == null) {
            log.warn("build-persona para {} sin respuestas: se ignora", userId)
            return
        }
        if (input.personaReady) return
        val summary = persona.build(input, userId)
        if (!repo.savePersona(userId, input.version, summary, clock.instant())) {
            log.info("build-persona de {} v{} ya estaba lista o quedó obsoleta: se descarta el resultado", userId, input.version)
        }
    }

    private fun buildPersonaTask(userId: UUID, version: Int) = TaskRequest(
        queue = QueueKind.AI_LIGHT,
        path = "/tasks/build-persona",
        // Un nombre por versión: un reenvío de respuestas genera tarea nueva; encolar dos veces la misma no duplica.
        name = "build-persona-$userId-v$version",
        body = buildJsonObject { put("id", JsonPrimitive(userId.toString())) }.toString(),
    )

    private fun TwinState.toDto() = TwinDto(
        traits = traits?.let { values -> TraitKey.entries.mapIndexed { i, key -> Trait(key, values[i]) } }.orEmpty(),
        level = level,
        xp = xp,
        xpNext = xpNextFor(xp),
        version = version,
        personaReady = personaReady,
        accuracy = accuracy,
    )

    companion object {
        private val EMPTY_STATE = TwinState(traits = null, personaReady = false, version = 1, level = 1, xp = 0, accuracy = null)
    }
}
