package me.santiagoreina.parcheu.feature.safety

import me.santiagoreina.parcheu.contract.BlockedDto
import me.santiagoreina.parcheu.contract.Mute
import me.santiagoreina.parcheu.contract.ReportCreated
import me.santiagoreina.parcheu.contract.ReportKind
import me.santiagoreina.parcheu.contract.ReportReason
import me.santiagoreina.parcheu.contract.ReportRequest
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall

/** Reportar, bloquear y silenciar. Las pruebas usan `FakeSafetyRepository`. */
interface SafetyRepository {
    /**
     * Reporta a [target]. Con [chatId] el servidor adjunta los últimos 20 mensajes de ese chat; quien no
     * quiere adjuntarlos lo pasa nulo.
     */
    suspend fun report(
        target: SafetyTarget,
        reason: ReportReason,
        details: String?,
        chatId: String?
    ): ApiResult<ReportCreated>

    suspend fun block(target: SafetyTarget): ApiResult<Unit>

    suspend fun unblock(userId: String): ApiResult<Unit>

    suspend fun blocks(): ApiResult<List<BlockedDto>>

    suspend fun setMuted(chatId: String, muted: Boolean): ApiResult<Unit>
}

class SafetyRepositoryImpl(private val api: SafetyApi, private val errorMapper: ApiErrorMapper) :
    SafetyRepository {
    override suspend fun report(
        target: SafetyTarget,
        reason: ReportReason,
        details: String?,
        chatId: String?
    ) = apiCall(errorMapper) { api.report(target.toRequest(reason, details, chatId)) }

    override suspend fun block(target: SafetyTarget) = apiCall(errorMapper) { api.block(target.id) }

    override suspend fun unblock(userId: String) = apiCall(errorMapper) { api.unblock(userId) }

    override suspend fun blocks() = apiCall(errorMapper) { api.blocks() }

    override suspend fun setMuted(chatId: String, muted: Boolean) =
        apiCall(errorMapper) { api.mute(chatId, Mute(muted)) }
}

/** Cuerpo de `POST /v1/reports`: un solo objetivo, el uuid o el `anonId` según cómo se ve a la persona. */
internal fun SafetyTarget.toRequest(reason: ReportReason, details: String?, chatId: String?) =
    ReportRequest(
        kind = ReportKind.USER,
        targetUserId = id.takeUnless { anonymous },
        anonId = id.takeIf { anonymous },
        chatId = chatId,
        reason = reason,
        details = details?.trim()?.takeIf { it.isNotEmpty() }
    )
