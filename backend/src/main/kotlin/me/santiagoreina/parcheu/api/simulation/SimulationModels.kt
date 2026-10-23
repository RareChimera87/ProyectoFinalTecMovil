package me.santiagoreina.parcheu.api.simulation

import kotlinx.serialization.Serializable
import me.santiagoreina.parcheu.contract.Dimension
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Progress
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import java.time.Duration
import java.time.Instant
import java.util.UUID

/* Modelo interno del motor de simulación (arquitectura.md §7.2, ADR-0009). */

/** Escenarios por simulación. */
const val SCENARIOS_PER_SIMULATION = 3

/** Turnos máximos por escenario (alternados A, B, A, ...). */
const val MAX_TURNS_PER_SCENARIO = 8

/** Desde este turno (1-based) un agente puede cerrar la conversación con [END_MARK]. */
const val MIN_TURN_TO_END = 4

/** Marca con la que un agente cierra el escenario. */
const val END_MARK = "[FIN]"

/** Techo de tokens visibles por turno (backlog E4.3). */
const val TURN_MAX_TOKENS = 120

/** Intentos por llamada al LLM (turno o juez) antes de dar la simulación por fallida. */
const val LLM_ATTEMPTS = 3

/** Memorias aceptadas como máximo en el prompt de un agente (arquitectura.md §7.2, punto 4). */
const val MAX_MEMORIES_IN_PROMPT = 10

/** Vigencia de la caché por (par, modo, versión A, versión B). */
val CACHE_TTL: Duration = Duration.ofDays(7)

/** Lado de la simulación: A es `user_a` y B es `user_b`. */
@Serializable
enum class Side { A, B }

/** Un turno guardado en `simulations.transcript`. */
@Serializable
data class StoredTurn(val speaker: Side, val text: String)

/** Ítem de la rúbrica de un escenario (`scenarios.rubric`). */
@Serializable
data class RubricItem(val name: String, val weight: Double = 0.0, val description: String = "")

/** Veredicto del juez para un escenario (salida JSON validada, arquitectura.md §7.2, punto 6). */
@Serializable
data class JudgeVerdict(
    val affinity: Int,
    val dimensions: List<Dimension> = emptyList(),
    val reasons: List<String> = emptyList(),
    val frictions: List<String> = emptyList(),
    val summary: String = "",
)

/** Un escenario dentro de la transcripción: sus turnos y, al terminar, el veredicto del juez. */
@Serializable
data class StoredScenario(
    val scenarioId: Int,
    val title: String,
    val turns: List<StoredTurn> = emptyList(),
    /** Un agente cerró con [END_MARK] o se llegó a [MAX_TURNS_PER_SCENARIO]. */
    val finished: Boolean = false,
    val verdict: JudgeVerdict? = null,
)

/**
 * Contenido de `simulations.transcript` (jsonb con datos personales, `PersonalJsonPaths`). Se crea
 * con los escenarios elegidos al aceptar la simulación y se completa turno a turno: así reintentar
 * la tarea retoma donde iba sin repetir turnos.
 */
@Serializable
data class StoredTranscript(val scenarios: List<StoredScenario> = emptyList()) {
    /** Turnos y veredictos guardados: crece en 1 con cada paso del motor (control de concurrencia). */
    val steps: Int get() = scenarios.sumOf { it.turns.size + if (it.verdict != null) 1 else 0 }

    /** Intercambia A y B (copia de caché con el par en orden inverso). */
    fun swapped(): StoredTranscript = copy(
        scenarios = scenarios.map { s ->
            s.copy(turns = s.turns.map { it.copy(speaker = if (it.speaker == Side.A) Side.B else Side.A) })
        },
    )
}

/** Contenido de `simulations.report` (jsonb con datos personales). E4.6 lo filtra por plan. */
@Serializable
data class StoredReport(
    val score: Int,
    /** `100 · (1 − ‖tA − tB‖₂ / √6)`. */
    val traitSimilarity: Double,
    /** Promedio de la afinidad del juez en los escenarios. */
    val judgeAverage: Double,
    val summary: String,
    val reasons: List<String> = emptyList(),
    val frictions: List<String> = emptyList(),
    val dimensions: List<Dimension> = emptyList(),
    val scenarioScores: List<Int> = emptyList(),
)

/** Tokens y costo acumulados de una simulación (`simulations.tokens_*`, `cost_micros`, `model`). */
data class UsageTotals(val tokensIn: Int = 0, val tokensOut: Int = 0, val costMicros: Long = 0, val model: String? = null) {
    operator fun plus(other: UsageTotals) = UsageTotals(
        tokensIn = tokensIn + other.tokensIn,
        tokensOut = tokensOut + other.tokensOut,
        costMicros = costMicros + other.costMicros,
        model = other.model ?: model,
    )
}

/** Fila de `simulations`. */
data class SimulationRecord(
    val id: UUID,
    val mode: Mode,
    val userA: UUID,
    val userB: UUID,
    val requestedBy: UUID?,
    val origin: SimulationOrigin,
    val status: SimulationStatus,
    val progress: Progress,
    val score: Int?,
    val report: StoredReport?,
    val transcript: StoredTranscript,
    val twinVersionA: Int?,
    val twinVersionB: Int?,
    val cachedFrom: UUID?,
    val provider: String?,
    val usage: UsageTotals,
    val error: String?,
    val createdAt: Instant,
    val finishedAt: Instant?,
) {
    fun sideOf(userId: UUID): Side? = when (userId) {
        userA -> Side.A
        userB -> Side.B
        else -> null
    }

    fun userOf(side: Side): UUID = if (side == Side.A) userA else userB

    fun otherThan(userId: UUID): UUID = if (userId == userA) userB else userA
}

/** Lo que el motor necesita del gemelo de una persona. Nunca su perfil (nombre, correo, edad). */
data class TwinProfile(
    val userId: UUID,
    val version: Int,
    /** 6 rasgos en el orden de `TraitKey`. */
    val traits: List<Double>,
    val persona: String?,
    val personaReady: Boolean,
)

/** Memoria aceptada del gemelo con su modo (ADR-0032). */
data class TwinMemory(val mode: Mode, val text: String)

/** Un escenario de `scenarios`. */
data class ScenarioDef(
    val id: Int,
    val mode: Mode,
    val slug: String,
    val title: String,
    val prompt: String,
    val isBasic: Boolean,
    val rubric: List<RubricItem>,
)

/** Datos de cuenta que valida `POST /v1/simulations`. */
data class Participant(
    val id: UUID,
    val firebaseUid: String,
    val activeModes: Set<Mode>,
    val ageVerified: Boolean,
    val parejaConfirmed: Boolean,
    val isDemo: Boolean,
    val deleted: Boolean,
)

/** Progreso inicial: primer escenario, turno 0. */
fun initialProgress(): Progress = Progress(scenarioIndex = 0, turn = 0, of = MAX_TURNS_PER_SCENARIO)
