package me.santiagoreina.parcheu.contract

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/* Gemelo digital (`/v1/twin/...`): preguntas, rasgos, evolución, memorias y feedback. */

/** Respuesta a una pregunta del gemelo: A, B o DEPENDE. */
@Serializable
public enum class TwinAnswer { A, B, DEPENDS }

/** Los 6 rasgos de `TraitEngine` (arquitectura.md §7.1). */
@Serializable
public enum class TraitKey { SOCIAL_ENERGY, PLANNING, CONFLICT_HANDLING, HUMOR, ACADEMIC_AMBITION, OPENNESS }

@Serializable
public enum class MemoryStatus { PENDING, ACCEPTED, REJECTED }

@Serializable
public enum class FeedbackKind { RATING, CONTROL }

/** GET /v1/twin/questions (lista). */
@Serializable
public data class QuestionDto(
    val id: Int,
    val text: String,
    val optionA: String,
    val optionB: String,
)

@Serializable
public data class Answer(
    val questionId: Int,
    val answer: TwinAnswer,
)

/** POST /v1/twin/answers (12 respuestas). */
@Serializable
public data class Answers(
    val items: List<Answer>,
)

/** Valor de un rasgo en [0, 1]. */
@Serializable
public data class Trait(
    val key: TraitKey,
    val value: Double,
)

/** GET /v1/twin. `accuracy` (0-100) es nulo mientras no haya datos semanales. */
@Serializable
public data class TwinDto(
    val traits: List<Trait> = emptyList(),
    val level: Int,
    val xp: Int,
    val xpNext: Int,
    val version: Int,
    val personaReady: Boolean,
    val accuracy: Int? = null,
)

/** Punto de GET /v1/twin/evolution; `month` en formato `yyyy-MM`. */
@Serializable
public data class TwinPoint(
    val month: String,
    val accuracy: Int? = null,
    val level: Int,
)

/** Memoria candidata del gemelo, siempre de un modo (ADR-0032). */
@Serializable
public data class MemoryDto(
    val id: String,
    val text: String,
    val mode: Mode,
    val status: MemoryStatus,
    val createdAt: Instant,
)

/** GET /v1/twin/feedback-prompts (lista). `options` solo en CONTROL. */
@Serializable
public data class FeedbackPrompt(
    val id: String,
    val kind: FeedbackKind,
    val text: String,
    val options: List<String>? = null,
)

/** POST /v1/twin/feedback: `rating` (RATING) o `answer` (CONTROL). */
@Serializable
public data class Feedback(
    val promptId: String,
    val rating: Int? = null,
    val answer: String? = null,
)
