package me.santiagoreina.parcheu.contract

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/* Inicio (C1) y cuotas (`/v1/home`, `/v1/quota`). */

/** Uso de un cupo; `limit` nulo = ilimitado (ParcheU+). */
@Serializable
public data class Usage(
    val used: Int,
    val limit: Int? = null,
    val resetsAt: Instant,
)

/** GET /v1/quota */
@Serializable
public data class QuotaDto(
    val simulations: Usage,
    val randomMatch: Usage,
    val fullReport: Usage,
    val plus: Boolean,
)

/**
 * Sugerencia de persona para simular en Inicio. `mutualCount` (amigos en común) es dato
 * premium: se omite (null) para usuarios gratis. `reason` es texto libre sin conteos ni
 * datos premium.
 */
@Serializable
public data class Suggestion(
    val other: PersonRef,
    val mode: Mode,
    val estimatedAffinity: Int? = null,
    val reason: String? = null,
    val mutualCount: Int? = null,
)

/** GET /v1/home */
@Serializable
public data class HomeDto(
    val quota: QuotaDto,
    val running: SimulationSummary? = null,
    val ready: List<SimulationSummary> = emptyList(),
    val suggestions: List<Suggestion> = emptyList(),
    val unread: Int = 0,
    val plus: Boolean,
)
