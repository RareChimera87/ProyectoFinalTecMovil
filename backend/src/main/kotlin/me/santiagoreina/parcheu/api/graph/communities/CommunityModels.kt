package me.santiagoreina.parcheu.api.graph.communities

import me.santiagoreina.parcheu.api.graph.PersonRow
import me.santiagoreina.parcheu.contract.Mode
import java.time.LocalDate
import java.util.UUID

/** Arista CONNECTED entre dos personas, tal como la lee el job (el repositorio ya quitó Pareja, HIDDEN, borrados y bloqueos). */
data class CommunityEdge(val a: PersonRow, val b: PersonRow, val mode: Mode, val affinity: Int?)

/** Una comunidad guardada (`communities` + `community_members`). */
data class StoredCommunity(
    val id: UUID,
    val week: LocalDate,
    val name: String,
    val stableKey: String,
    val members: Set<UUID>,
)

/**
 * Cambios que deja el job de la semana: [kept] conserva id, nombre y `stable_key` (Jaccard >= 0,5 con la
 * semana anterior) y solo cambia sus miembros y la semana; [created] son comunidades nuevas; [removed] las
 * que ya no existen.
 */
data class CommunityPlan(
    val kept: List<StoredCommunity>,
    val created: List<StoredCommunity>,
    val removed: Set<UUID>,
)

/** Resumen de una corrida del job (para el log y las pruebas). */
data class CommunityRunSummary(val week: LocalDate, val communities: Int, val kept: Int, val created: Int, val removed: Int)
