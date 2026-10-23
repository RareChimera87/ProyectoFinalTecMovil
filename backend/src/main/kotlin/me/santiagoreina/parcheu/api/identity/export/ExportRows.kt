package me.santiagoreina.parcheu.api.identity.export

import me.santiagoreina.parcheu.api.identity.ConsentRecord
import me.santiagoreina.parcheu.contract.Answer
import me.santiagoreina.parcheu.contract.ChatType
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.EntitlementState
import me.santiagoreina.parcheu.contract.MemoryDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
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
)

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
