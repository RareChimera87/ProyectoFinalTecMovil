package me.santiagoreina.parcheu.contract

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/*
 * Red social (E6): invitaciones por QR y código, presentaciones, amigos de amigos,
 * comunidades, evolución y 'quién quiere conectar contigo'. Los `items` son premium:
 * los usuarios gratis solo reciben `count`.
 */

@Serializable
public enum class IntroductionStatus { SIMULATING, PROPOSED, CONNECTED, DECLINED, FAILED }

@Serializable
public enum class RedeemStatus { PENDING, CONNECTED }

@Serializable
public enum class MomentKind {
    FIRST_CONNECTION,
    FIRST_COMMUNITY,
    CONNECTIONS_10,
    CONNECTIONS_25,
    CONNECTIONS_50,
    HIGHEST_AFFINITY,
}

/**
 * Candidato de amigo de amigo; la afinidad es una estimación (ADR-0023).
 *
 * [viaId] es el id (el de `NodeDto.id` del ego-grafo) del amigo en común por el que se llega: el
 * origen de la línea punteada de la capa fof de G0 y el "Amigo de …" de D6. Sale de una arista
 * CONNECTED que no es de Pareja entre ese amigo y el candidato (la misma fila de la consulta de
 * arquitectura.md §6.2), nunca de otra. Es opcional para que un cliente o servidor viejo siga
 * funcionando.
 */
@Serializable
public data class FofCandidate(
    val anonId: String,
    val label: String,
    val estimatedAffinity: Int,
    val mutuals: Int,
    val mode: Mode,
    val viaId: String? = null,
)

/** GET /v1/graph/fof */
@Serializable
public data class FofDto(
    val count: Int,
    val items: List<FofCandidate>? = null,
)

/** Resumen mensual derivado de las aristas; `month` en formato `yyyy-MM`. */
@Serializable
public data class MonthSnapshot(
    val month: String,
    val connections: Int,
    val newConnections: Int,
    val nodes: Int,
)

/** Momento de la red (arquitectura.md §6.4). `value`: conexiones o afinidad según el tipo. */
@Serializable
public data class Moment(
    val kind: MomentKind,
    val at: Instant,
    val label: String,
    val value: Int? = null,
    val other: PersonRef? = null,
)

/** GET /v1/graph/history (ParcheU+). */
@Serializable
public data class HistoryDto(
    val months: List<MonthSnapshot> = emptyList(),
    val moments: List<Moment> = emptyList(),
)

/** Comunidad semanal; `traits` = rasgos o modos que la describen. */
@Serializable
public data class CommunityDto(
    val id: String,
    val name: String,
    val members: List<PersonRef> = emptyList(),
    val size: Int,
    val traits: List<String> = emptyList(),
)

/** GET /v1/communities */
@Serializable
public data class CommunitiesDto(
    val count: Int,
    val items: List<CommunityDto>? = null,
)

@Serializable
public data class ConnectRequest(
    val simulationId: String,
    val other: PersonRef,
    val affinity: Int,
)

/** GET /v1/connect-requests */
@Serializable
public data class ConnectRequestsDto(
    val count: Int,
    val items: List<ConnectRequest>? = null,
)

/** POST /v1/introductions (Amistad o Estudio). */
@Serializable
public data class Introduce(
    val mode: Mode,
    val userA: String,
    val userB: String,
)

/**
 * POST/GET /v1/introductions[/{id}]; `myResponse` nulo = sin responder. `introducer` es quien
 * presenta y `other` la persona propuesta al usuario (E3); ambos permiten Reportar o bloquear (E8.1).
 */
@Serializable
public data class IntroductionDto(
    val id: String,
    val mode: Mode,
    val introducer: PersonRef,
    val other: PersonRef,
    val status: IntroductionStatus,
    val simulationId: String? = null,
    val report: ReportDto? = null,
    val myResponse: Boolean? = null,
)

/** POST /v1/introductions/{id}/response */
@Serializable
public data class IntroResponse(
    val interested: Boolean,
)

/** POST /v1/introductions/request (ParcheU+). */
@Serializable
public data class IntroRequest(
    val anonId: String,
)

/**
 * GET /v1/invites/me; código `XXXX-0000` y su App Link `https://<hosting>/i/{codigo}`. [pending] son los
 * canjes de mi código que esperan que yo los acepte (E6.1), los más recientes primero.
 */
@Serializable
public data class InviteDto(
    val code: String,
    val link: String,
    val pending: List<PendingInvite> = emptyList(),
)

/**
 * Alguien canjeó mi código en [mode] y espera que lo acepte. [id] identifica ese canje (persona y modo,
 * `<anonId>:<MODO>`): es lo que va en `POST /v1/invites/{id}/accept` y acepta solo ese modo; si la misma
 * persona canjeó en otro modo (p. ej. Pareja) es otro [PendingInvite] con otro [id]. Quien canjea un código
 * pide conectar con su dueño, así que el dueño ve su nombre (`other.label`), pero no su `id` ni su foto
 * hasta aceptar.
 */
@Serializable
public data class PendingInvite(
    val id: String,
    val other: PersonRef,
    val mode: Mode,
    val createdAt: Instant,
)

/** POST /v1/invites/redeem */
@Serializable
public data class Redeem(
    val code: String,
    val mode: Mode,
)

/** Respuesta de redeem y de accept. */
@Serializable
public data class RedeemResult(
    val status: RedeemStatus,
    val other: PersonRef,
)
