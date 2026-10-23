package me.santiagoreina.parcheu.contract

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/*
 * Simulaciones, informe, transcripción y decisión de conectar (F4-F8).
 * Los campos premium (`reasons`, `frictions`, `dimensions`) son nulos para usuarios gratis
 * y no aparecen en el JSON.
 */

@Serializable
public enum class SimulationStatus { QUEUED, RUNNING, DONE, FAILED }

@Serializable
public enum class SimulationOrigin { RANDOM, PARCHE, INTRODUCTION, SCENARIO }

@Serializable
public enum class Speaker { ME, OTHER }

@Serializable
public enum class DecisionStatus { WAITING, CONNECTED, DECLINED }

/** POST /v1/simulations */
@Serializable
public data class StartSimulation(
    val otherUserId: String,
    val mode: Mode,
)

/** Resumen de una simulación (202 de POST /v1/simulations, listas e Inicio). `score` solo si DONE. */
@Serializable
public data class SimulationSummary(
    val id: String,
    val status: SimulationStatus,
    val mode: Mode,
    val other: PersonRef,
    val createdAt: Instant,
    val origin: SimulationOrigin? = null,
    val score: Int? = null,
)

/** Progreso: escenario actual (0-based), turno y total de turnos. */
@Serializable
public data class Progress(
    val scenarioIndex: Int,
    val turn: Int,
    val of: Int,
)

/** GET /v1/simulations/{id} */
@Serializable
public data class SimulationDto(
    val summary: SimulationSummary,
    val progress: Progress,
    val score: Int? = null,
)

/** Dimensión de la rúbrica del juez (0-100). */
@Serializable
public data class Dimension(
    val name: String,
    val score: Int,
    val note: String? = null,
)

/** GET /v1/simulations/{id}/report y POST .../report:unlock */
@Serializable
public data class ReportDto(
    val score: Int,
    val summary: String,
    val reasons: List<String>? = null,
    val frictions: List<String>? = null,
    val dimensions: List<Dimension>? = null,
    val locked: Boolean,
    val unlockAvailable: Boolean,
    val myDecision: Boolean? = null,
    val otherDecided: Boolean = false,
    val otherName: String? = null,
)

@Serializable
public data class Turn(
    val speaker: Speaker,
    val text: String,
)

@Serializable
public data class ScenarioTranscript(
    val title: String,
    val turns: List<Turn> = emptyList(),
)

/** GET /v1/simulations/{id}/transcript (ParcheU+). */
@Serializable
public data class TranscriptDto(
    val scenarios: List<ScenarioTranscript> = emptyList(),
)

/** POST /v1/simulations/{id}/decision */
@Serializable
public data class Decision(
    val connect: Boolean,
)

@Serializable
public data class DecisionResult(
    val status: DecisionStatus,
    val chatId: String? = null,
)
