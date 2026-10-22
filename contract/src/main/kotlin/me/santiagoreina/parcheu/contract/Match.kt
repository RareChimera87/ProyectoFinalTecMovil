package me.santiagoreina.parcheu.contract

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/* Fila de match, match encontrado (F1, F2) y modo escenario (F3) (arquitectura.md §7.4-7.6). */

/** Estado del ticket en la fila; espejo de `matchTickets/{uid}` en Firestore. */
@Serializable
public enum class TicketStatus { WAITING, MATCHED, EXPIRED }

@Serializable
public enum class MatchStatus { PENDING_CHOICE, SIMULATION, SCENARIO, EXPIRED }

@Serializable
public enum class MatchChoice { SCENARIO, SIMULATION }

@Serializable
public enum class ScenarioSessionStatus { OPEN, WAITING_OTHER, JUDGING, DONE }

/** POST /v1/match/queue */
@Serializable
public data class Enqueue(
    val mode: Mode,
)

/** POST/GET /v1/match/queue */
@Serializable
public data class TicketDto(
    val status: TicketStatus,
    val mode: Mode,
    val expiresAt: Instant,
    val matchId: String? = null,
)

/** Perfil anónimo del match; sin nombre, foto, carrera ni id interno. `ageRange`: 18-19, 20-22, 23-25 o 26+. */
@Serializable
public data class AnonProfile(
    val label: String,
    val ageRange: String,
    val university: String? = null,
    val interests: List<String> = emptyList(),
    val isDemo: Boolean = false,
)

/** GET /v1/matches/{id} */
@Serializable
public data class MatchDto(
    val id: String,
    val mode: Mode,
    val other: AnonProfile,
    val status: MatchStatus,
    val myChoice: MatchChoice? = null,
    val decideBy: Instant,
    val scenarioAvailable: Boolean = false,
)

/** POST /v1/matches/{id}/choice */
@Serializable
public data class Choice(
    val choice: MatchChoice,
)

/** GET /v1/scenarios?mode= (lista). */
@Serializable
public data class ScenarioDto(
    val id: Int,
    val title: String,
    val isBasic: Boolean,
    val locked: Boolean = false,
)

/** POST /v1/scenario-sessions/{chatId}/agreement */
@Serializable
public data class ScenarioSessionDto(
    val status: ScenarioSessionStatus,
    val simulationId: String? = null,
)
