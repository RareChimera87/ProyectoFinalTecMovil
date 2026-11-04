package me.santiagoreina.parcheu.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

/* Moderación (`/v1/admin/...`, claim `admin`). */

@Serializable
public enum class AdminAction { DISMISS, HIDE_MESSAGE, SUSPEND_24H, BAN }

@Serializable
public enum class ReportStatus { OPEN, RESOLVED }

@Serializable
public enum class ReportSource { USER, AUTO }

/** POST /v1/admin/reports/{id}/resolve */
@Serializable
public data class Resolve(
    val action: AdminAction,
    val note: String? = null,
)

/** Reporte visto por un admin; `snapshot` = últimos mensajes adjuntos (dato personal). */
@Serializable
public data class AdminReport(
    val id: String,
    val kind: ReportKind,
    val status: ReportStatus,
    val source: ReportSource,
    val reason: ReportReason,
    val details: String? = null,
    val priority: Int = 0,
    val reporterId: String? = null,
    val targetId: String? = null,
    val chatId: String? = null,
    val messageId: String? = null,
    val simulationId: String? = null,
    val turnIndex: Int? = null,
    val snapshot: JsonElement? = null,
    val resolution: String? = null,
    val createdAt: Instant,
    val resolvedAt: Instant? = null,
)
