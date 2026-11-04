package me.santiagoreina.parcheu.contract

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/*
 * Centro de notificaciones (C2). `kind` es texto (y no enum) para que un cliente viejo no
 * falle al recibir un tipo nuevo; los valores conocidos están en [NotificationKinds].
 */

public object NotificationKinds {
    public const val SIMULATION_READY: String = "SIMULATION_READY"
    public const val SIMULATION_FAILED: String = "SIMULATION_FAILED"
    public const val MATCH_FOUND: String = "MATCH_FOUND"
    public const val MATCH_EXPIRED: String = "MATCH_EXPIRED"
    public const val CONNECT_REQUEST: String = "CONNECT_REQUEST"
    public const val NEW_CONNECTION: String = "NEW_CONNECTION"
    public const val INTRODUCTION_PROPOSAL: String = "INTRODUCTION_PROPOSAL"

    /** A quien presenta: la simulación de su presentación terminó (E2) o falló. */
    public const val INTRODUCTION_READY: String = "INTRODUCTION_READY"

    /** Al amigo en común: alguien de su parche le pide una presentación (D6, ParcheU+). */
    public const val INTRODUCTION_REQUEST: String = "INTRODUCTION_REQUEST"
    public const val INVITE_PENDING: String = "INVITE_PENDING"
    public const val CHAT_MESSAGE: String = "CHAT_MESSAGE"
    public const val TWIN_MEMORIES: String = "TWIN_MEMORIES"
    public const val TWIN_FEEDBACK: String = "TWIN_FEEDBACK"
    public const val COMMUNITY: String = "COMMUNITY"
    public const val ACCOUNT: String = "ACCOUNT"
}

/** Aviso. `route` es el deep link `parcheu://...`; `locked` = contenido de ParcheU+. */
@Serializable
public data class NotificationDto(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val route: String? = null,
    val actor: PersonRef? = null,
    val locked: Boolean = false,
    val read: Boolean = false,
    val createdAt: Instant,
)

/** GET /v1/notifications agrupado en Hoy, Esta semana y anteriores. */
@Serializable
public data class NotificationsDto(
    val today: List<NotificationDto> = emptyList(),
    val week: List<NotificationDto> = emptyList(),
    val older: List<NotificationDto> = emptyList(),
)

/** POST /v1/notifications/read; `ids` nulo o vacío = todas. */
@Serializable
public data class MarkRead(
    val ids: List<String>? = null,
)
