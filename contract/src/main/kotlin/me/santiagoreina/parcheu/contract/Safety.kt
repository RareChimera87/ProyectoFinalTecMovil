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

/** Mensaje propio dentro de la exportación; sin `senderId` ni textos de terceros (E8.3). */
@Serializable
public data class ExportMessage(
    val id: String,
    val text: String,
    val createdAt: Instant,
)

/** Chat dentro de la exportación: solo los mensajes propios del usuario, nunca los de terceros. */
@Serializable
public data class ExportChat(
    val id: String,
    val type: ChatType,
    val mode: Mode,
    val messages: List<ExportMessage> = emptyList(),
)

/** Conexión dentro de la exportación; sin ids internos de la otra persona. */
@Serializable
public data class ExportConnection(
    val label: String,
    val mode: Mode,
    val state: EdgeState,
    val connectedAt: Instant? = null,
)

/** Reporte que la persona hizo (E8.1). De la persona reportada solo sale su `anonId`; sin instantánea ni notas de moderación. */
@Serializable
public data class ExportReport(
    val id: String,
    val kind: ReportKind,
    val reason: ReportReason,
    /** Texto libre que escribió quien reporta. */
    val details: String? = null,
    val reportedAnonId: String? = null,
    val status: ReportStatus,
    val createdAt: Instant,
    val resolvedAt: Instant? = null,
)

@Serializable
public enum class ExportIntroRole { MADE, RECEIVED }

/**
 * Presentación (E6.2) hecha por la persona ([ExportIntroRole.MADE]: `people` son las dos personas presentadas) o
 * recibida ([ExportIntroRole.RECEIVED]: `people` es la otra persona presentada y `introducerAnonId` quien presentó).
 * Solo `anonId`. `status` solo en las hechas; en las recibidas solo la respuesta propia, porque el estado y la
 * respuesta de la otra persona revelarían su decisión.
 */
@Serializable
public data class ExportIntroduction(
    val id: String,
    val role: ExportIntroRole,
    val mode: Mode,
    val people: List<String> = emptyList(),
    val introducerAnonId: String? = null,
    val status: IntroductionStatus? = null,
    /** `INTERESTED` o `DECLINED`; solo la respuesta propia. */
    val myResponse: String? = null,
    val createdAt: Instant,
)

/** Match (E4) de la persona: fechas, modo, estado y su propia elección (no la de la otra persona). */
@Serializable
public data class ExportMatch(
    val id: String,
    val mode: Mode,
    val status: MatchStatus,
    val otherAnonId: String,
    val myChoice: MatchChoice? = null,
    val createdAt: Instant,
    val decideBy: Instant,
)

/** Su turno vigente en la fila de emparejamiento. */
@Serializable
public data class ExportQueueTicket(
    val mode: Mode,
    val status: TicketStatus,
    val enqueuedAt: Instant,
    val expiresAt: Instant,
)

/** Calificación o control de su gemelo (E3). */
@Serializable
public data class ExportTwinFeedback(
    val id: String,
    val kind: FeedbackKind,
    val simulationId: String? = null,
    val turnIndex: Int? = null,
    val questionId: Int? = null,
    val predicted: String? = null,
    val actual: String? = null,
    val rating: Int? = null,
    val createdAt: Instant,
)

@Serializable
public enum class ExportInviteRole { OWNER, REDEEMER }

/** Canje de un código de invitación; `otherAnonId` es la otra parte. */
@Serializable
public data class ExportInviteRedemption(
    val role: ExportInviteRole,
    val otherAnonId: String,
    val mode: Mode,
    val status: RedeemStatus,
    val createdAt: Instant,
    val acceptedAt: Instant? = null,
)

/** Su código de invitación, los canjes en los que participa y las fechas de sus intentos de canje. */
@Serializable
public data class ExportInvites(
    val code: String? = null,
    val redemptions: List<ExportInviteRedemption> = emptyList(),
    val redeemAttempts: List<Instant> = emptyList(),
)

/** Evento de compra de la persona (RTDN, E7.1): solo fecha y tipo de notificación de Play. */
@Serializable
public data class ExportPurchase(
    val receivedAt: Instant,
    val notificationType: Int? = null,
)

/**
 * GET /v1/me/export: copia completa de los datos del usuario (E8.3, Ley 1581).
 *
 * Los campos desde `twinPersonaSummary` son opcionales con valor por defecto: un cliente que no los conozca los
 * ignora (`ContractJson.ignoreUnknownKeys`). Qué se **excluye** y por qué:
 * - Todo dato de otras personas (uuid, nombre, correo, foto, mensajes, respuestas a presentaciones, su elección
 *   en un match): solo sale su `anonId`. Los avisos que citan a terceros salen con el texto reemplazado.
 * - Tokens: `purchaseToken` de Play y su payload (solo se da fecha y tipo), tokens FCM y `firebase_uid`.
 * - Reportes de los que la persona es el reportado, y la instantánea de mensajes de cualquier reporte (texto de
 *   terceros), el id de quien resolvió y las notas de moderación.
 * - Datos operativos sin contenido personal propio: cuotas y uso de IA, versiones y exactitud del gemelo,
 *   cursores internos y desbloqueos de informes.
 */
@Serializable
public data class ExportDto(
    val generatedAt: Instant,
    val me: MeDto,
    val consents: List<ConsentState> = emptyList(),
    val twin: TwinDto? = null,
    val twinAnswers: List<Answer> = emptyList(),
    val memories: List<MemoryDto> = emptyList(),
    val simulations: List<SimulationSummary> = emptyList(),
    val connections: List<ExportConnection> = emptyList(),
    val chats: List<ExportChat> = emptyList(),
    val notifications: List<NotificationDto> = emptyList(),
    val blocks: List<BlockedDto> = emptyList(),
    val entitlement: EntitlementDto? = null,
    /** Resumen de su persona que escribió la IA (`twins.persona_summary`). */
    val twinPersonaSummary: String? = null,
    val reportsMade: List<ExportReport> = emptyList(),
    val introductions: List<ExportIntroduction> = emptyList(),
    val matches: List<ExportMatch> = emptyList(),
    val queueTicket: ExportQueueTicket? = null,
    val twinFeedback: List<ExportTwinFeedback> = emptyList(),
    val invites: ExportInvites? = null,
    val purchases: List<ExportPurchase> = emptyList(),
)
