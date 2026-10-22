package me.santiagoreina.parcheu.feature.safety

import me.santiagoreina.parcheu.contract.BlockedDto
import me.santiagoreina.parcheu.contract.HelpResource
import me.santiagoreina.parcheu.contract.ReportCreated
import me.santiagoreina.parcheu.contract.ReportReason
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

/** Repositorio de seguridad en memoria que registra cada llamada. */
class FakeSafetyRepository : SafetyRepository {
    data class ReportCall(val target: SafetyTarget, val reason: ReportReason, val details: String?, val chatId: String?)

    val reports = mutableListOf<ReportCall>()
    val blocks = mutableListOf<SafetyTarget>()
    val unblocks = mutableListOf<String>()
    val mutes = mutableListOf<Pair<String, Boolean>>()

    var reportResult: ApiResult<ReportCreated>? = null
    var blockResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var muteResult: ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun report(
        target: SafetyTarget,
        reason: ReportReason,
        details: String?,
        chatId: String?,
    ): ApiResult<ReportCreated> {
        reports += ReportCall(target, reason, details, chatId)
        return reportResult ?: ApiResult.Success(
            ReportCreated("r-${reports.size}", if (reason == ReportReason.SELF_HARM) HELP else emptyList()),
        )
    }

    override suspend fun block(target: SafetyTarget): ApiResult<Unit> {
        blocks += target
        return blockResult
    }

    override suspend fun unblock(userId: String): ApiResult<Unit> {
        unblocks += userId
        return ApiResult.Success(Unit)
    }

    override suspend fun blocks(): ApiResult<List<BlockedDto>> = ApiResult.Success(emptyList())

    override suspend fun setMuted(chatId: String, muted: Boolean): ApiResult<Unit> {
        mutes += chatId to muted
        return muteResult
    }

    companion object {
        val HELP =
            listOf(
                HelpResource(
                    "Línea 192, opción 4",
                    "192",
                    "Línea nacional de salud mental. Marca 192 y elige la opción 4.",
                ),
                HelpResource("Línea 106", "106", "Línea de escucha y orientación en crisis emocional."),
                HelpResource("Emergencias 123", "123", "Si tú o alguien más está en peligro inmediato."),
            )

        val failure = ApiResult.Failure(AppError.Network)

        val laura = SafetyTarget(id = "user-2", name = "Laura Pinzón", chatId = "chat-1")
    }
}
