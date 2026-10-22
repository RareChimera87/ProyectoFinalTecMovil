package me.santiagoreina.parcheu.contract

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/* Reportar, bloquear y exportar mis datos (E8). Reportar y bloquear es siempre gratis. */

@Serializable
public enum class ReportKind { USER, AI_CONTENT }

/** Motivos de H2; SELF_HARM devuelve `helpResources`. AI_HARMFUL es para contenido de la IA. */
@Serializable
public enum class ReportReason { HARASSMENT, UNWANTED_SEXUAL, UNDERAGE, FAKE_OR_SCAM, SELF_HARM, AI_HARMFUL, OTHER }

/**
 * POST /v1/reports. Exactamente un objetivo: `targetUserId`, `anonId` o
 * `simulationId` + `turnIndex` (AI_CONTENT). `chatId`/`messageId` dan contexto.
 */
@Serializable
public data class ReportRequest(
    val kind: ReportKind,
    val targetUserId: String? = null,
    val anonId: String? = null,
    val chatId: String? = null,
    val messageId: String? = null,
    val simulationId: String? = null,
    val turnIndex: Int? = null,
    val reason: ReportReason,
    val details: String? = null,
)

/** Línea de ayuda (Línea 192 opción 4, Línea 106, 123). */
@Serializable
public data class HelpResource(
    val name: String,
    val phone: String,
    val description: String? = null,
)

/** 201 de POST /v1/reports */
@Serializable
public data class ReportCreated(
    val id: String,
    val helpResources: List<HelpResource>? = null,
)

/** GET /v1/blocks (lista). */
@Serializable
public data class BlockedDto(
    val userId: String,
    val label: String,
    val initials: String,
    val blockedAt: Instant,
)

/** Chat con sus mensajes dentro de la exportación. */
@Serializable
public data class ExportChat(
    val id: String,
    val type: ChatType,
    val mode: Mode,
    val messages: List<MessageDto> = emptyList(),
)

/** GET /v1/me/export: copia completa de los datos del usuario (E8.3). */
@Serializable
public data class ExportDto(
    val generatedAt: Instant,
    val me: MeDto,
    val consents: List<ConsentState> = emptyList(),
    val twin: TwinDto? = null,
    val twinAnswers: List<Answer> = emptyList(),
    val memories: List<MemoryDto> = emptyList(),
    val simulations: List<SimulationSummary> = emptyList(),
    val chats: List<ExportChat> = emptyList(),
    val notifications: List<NotificationDto> = emptyList(),
    val blocks: List<BlockedDto> = emptyList(),
    val entitlement: EntitlementDto? = null,
)
