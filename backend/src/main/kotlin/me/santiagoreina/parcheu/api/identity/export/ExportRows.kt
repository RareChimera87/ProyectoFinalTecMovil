package me.santiagoreina.parcheu.api.identity.export

import me.santiagoreina.parcheu.api.identity.ConsentRecord
import me.santiagoreina.parcheu.contract.Answer
import me.santiagoreina.parcheu.contract.ChatType
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.EntitlementState
import me.santiagoreina.parcheu.contract.FeedbackKind
import me.santiagoreina.parcheu.contract.IntroductionStatus
import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.contract.MatchStatus
import me.santiagoreina.parcheu.contract.MemoryDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.RedeemStatus
import me.santiagoreina.parcheu.contract.ReportKind
import me.santiagoreina.parcheu.contract.ReportReason
import me.santiagoreina.parcheu.contract.ReportStatus
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.TicketStatus
import java.time.Instant
import java.util.UUID

/**
 * Lo que la exportación lee de Postgres para una persona. De los demás solo trae su uuid (que
 * [AccountExportService] convierte en `anonId`) y si es perfil demo: nunca nombre, correo, foto ni
 * mensajes de terceros.
 */
data class ExportRows(
    /** Historial completo de `consents` (no solo el estado vigente), en orden. */
    val consents: List<ConsentRecord> = emptyList(),
    val answers: List<Answer> = emptyList(),
    val memories: List<MemoryDto> = emptyList(),
    val simulations: List<ExportSimulationRow> = emptyList(),
    val connections: List<ExportConnectionRow> = emptyList(),
    val chats: List<ExportChatRow> = emptyList(),
    val blocks: List<ExportBlockRow> = emptyList(),
    val entitlement: ExportEntitlementRow? = null,
    val notifications: List<ExportNotificationRow> = emptyList(),
    val personaSummary: String? = null,
    val reportsMade: List<ExportReportRow> = emptyList(),
    val introductions: List<ExportIntroductionRow> = emptyList(),
    val matches: List<ExportMatchRow> = emptyList(),
    val queueTicket: ExportQueueTicketRow? = null,
    val twinFeedback: List<ExportTwinFeedbackRow> = emptyList(),
    val inviteCode: String? = null,
    val inviteRedemptions: List<ExportRedemptionRow> = emptyList(),
    val redeemAttempts: List<Instant> = emptyList(),
    val purchases: List<ExportPurchaseRow> = emptyList(),
)

/** Aviso de la bandeja; el servicio decide si su texto se puede exportar (puede citar a terceros). */
data class ExportNotificationRow(
    val id: UUID,
    val kind: String,
    val title: String,
    val body: String,
    val route: String?,
    val actorId: UUID?,
    val locked: Boolean,
    val createdAt: Instant,
    val readAt: Instant?,
)

/** Reporte hecho por la persona. Sin `snapshot` (mensajes de terceros) ni datos de moderación. */
data class ExportReportRow(
    val id: UUID,
    val kind: ReportKind,
    val reason: ReportReason,
    val details: String?,
    val targetId: UUID?,
    val status: ReportStatus,
    val createdAt: Instant,
    val resolvedAt: Instant?,
)

/** Presentación donde la persona es quien presenta ([introducerId] = ella) o una de las presentadas. */
data class ExportIntroductionRow(
    val id: UUID,
    val introducerId: UUID,
    val userA: UUID,
    val userB: UUID,
    val mode: Mode,
    val status: IntroductionStatus,
    /** Respuesta de la propia persona (`INTERESTED`/`DECLINED`) cuando es una de las presentadas. */
    val myResponse: String?,
    val createdAt: Instant,
)

data class ExportMatchRow(
    val id: UUID,
    val otherUserId: UUID,
    val mode: Mode,
    val status: MatchStatus,
    val myChoice: MatchChoice?,
    val createdAt: Instant,
    val decideBy: Instant,
)

data class ExportQueueTicketRow(val mode: Mode, val status: TicketStatus, val enqueuedAt: Instant, val expiresAt: Instant)

data class ExportTwinFeedbackRow(
    val id: UUID,
    val kind: FeedbackKind,
    val simulationId: UUID?,
    val turnIndex: Int?,
    val questionId: Int?,
    val predicted: String?,
    val actual: String?,
    val rating: Int?,
    val createdAt: Instant,
)

data class ExportRedemptionRow(
    val ownerId: UUID,
    val redeemerId: UUID,
    val mode: Mode,
    val status: RedeemStatus,
    val createdAt: Instant,
    val acceptedAt: Instant?,
)

/** Evento de compra ligado a la persona: nunca el token ni el payload, solo fecha y tipo. */
data class ExportPurchaseRow(val receivedAt: Instant, val notificationType: Int?)

data class ExportSimulationRow(
    val id: UUID,
    val status: SimulationStatus,
    val mode: Mode,
    val origin: SimulationOrigin,
    val otherUserId: UUID,
    val otherIsDemo: Boolean,
    val score: Int?,
    val createdAt: Instant,
)

data class ExportConnectionRow(val otherUserId: UUID, val mode: Mode, val state: EdgeState, val connectedAt: Instant?)

/** Chat con **solo** los mensajes propios ([ownMessages]). */
data class ExportChatRow(val id: UUID, val type: ChatType, val mode: Mode, val ownMessages: List<ExportMessageRow>)

data class ExportMessageRow(val id: UUID, val text: String, val createdAt: Instant)

data class ExportBlockRow(val blockedUserId: UUID, val createdAt: Instant)

data class ExportEntitlementRow(
    val state: EntitlementState,
    val basePlan: String?,
    val expiresAt: Instant?,
    val autoRenewing: Boolean,
)

/** Lectura de la exportación (implementación real en [ExposedExportRepository]). */
fun interface ExportRepository {
    suspend fun rows(userId: UUID): ExportRows
}
