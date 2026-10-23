package me.santiagoreina.parcheu.api.platform.db.tables

/*
 * Valores de columnas `text` con CHECK que no expone el contrato. Deben coincidir con los CHECK de
 * `V1__esquema.sql`; los demás enums (Mode, EdgeState, MatchStatus, ConsentKind, SimulationOrigin,
 * SimulationStatus...) se toman del contrato para no duplicarlos.
 */

/** `introductions.a_response` y `b_response`. */
enum class IntroResponse { INTERESTED, DECLINED }

/** `intro_requests.status`. */
enum class IntroRequestStatus { PENDING, ACCEPTED, DECLINED, EXPIRED }
