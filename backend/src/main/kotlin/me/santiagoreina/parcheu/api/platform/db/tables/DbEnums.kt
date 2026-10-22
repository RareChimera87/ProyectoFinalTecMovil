package me.santiagoreina.parcheu.api.platform.db.tables

/*
 * Valores de columnas `text` con CHECK que no expone el contrato. Deben coincidir con los CHECK de
 * `V1__esquema.sql`; los demás enums (Mode, EdgeState, MatchStatus...) se toman del contrato.
 */

/** `consents.kind` (ADR-0017). */
enum class ConsentKind { TERMS, PRIVACY, CHAT_LEARNING_AMISTAD, CHAT_LEARNING_PAREJA, CHAT_LEARNING_ESTUDIO }

/** `simulations.origin`. */
enum class SimulationOrigin { RANDOM, PARCHE, INTRODUCTION, SCENARIO }

/** `simulations.status`. */
enum class SimulationStatus { QUEUED, RUNNING, DONE, FAILED }

/** `introductions.a_response` y `b_response`. */
enum class IntroResponse { INTERESTED, DECLINED }

/** `intro_requests.status`. */
enum class IntroRequestStatus { PENDING, ACCEPTED, DECLINED, EXPIRED }
