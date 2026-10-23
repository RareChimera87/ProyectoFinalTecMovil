package me.santiagoreina.parcheu.feature.invites

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.InviteDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.PendingInvite
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.contract.RedeemResult
import me.santiagoreina.parcheu.contract.RedeemStatus
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

/** Invitaciones en memoria para las pruebas de C5. */
class FakeInvitesRepository(
    var invite: ApiResult<InviteDto> = ApiResult.Success(sampleInvite()),
    var redeemResult: ApiResult<RedeemResult> =
        ApiResult.Success(
            RedeemResult(
                RedeemStatus.PENDING,
                PersonRef(anonId = "a_x", label = "Sebastián", initials = "S")
            )
        ),
    var acceptResult: ApiResult<RedeemResult> =
        ApiResult.Success(
            RedeemResult(
                RedeemStatus.CONNECTED,
                PersonRef(id = "u-2", label = "Laura Pérez", initials = "LP")
            )
        )
) : InvitesRepository {
    val redeemed = mutableListOf<Pair<String, Mode>>()
    val accepted = mutableListOf<Pair<String, Mode>>()

    override suspend fun myInvite() = invite

    override suspend fun redeem(code: String, mode: Mode): ApiResult<RedeemResult> {
        redeemed += code to mode
        return redeemResult
    }

    override suspend fun accept(id: String, mode: Mode): ApiResult<RedeemResult> {
        accepted += id to mode
        return acceptResult
    }

    companion object {
        val pendingLaura = PendingInvite(
            id = "a_laura:ESTUDIO",
            other = PersonRef(anonId = "a_laura", label = "Laura Pérez", initials = "LP"),
            mode = Mode.ESTUDIO,
            createdAt = Instant.parse("2026-11-10T15:00:00Z")
        )

        /** Laura también canjeó en Pareja: es otro canje, con otro `id`. */
        val pendingLauraPareja = pendingLaura.copy(
            id = "a_laura:PAREJA",
            mode = Mode.PAREJA,
            createdAt = Instant.parse("2026-11-10T15:05:00Z")
        )

        fun sampleInvite(pending: List<PendingInvite> = emptyList()) =
            InviteDto("MARI-4827", "https://parcheu-dev.web.app/i/MARI-4827", pending)

        val networkFailure: ApiResult<Nothing> = ApiResult.Failure(AppError.Network)
    }
}

/** Lector de QR de prueba: devuelve [result] sin abrir la cámara. */
class FakeCodeScanner(var result: ScanResult) : CodeScanner {
    var scans = 0

    override suspend fun scan(): ScanResult {
        scans++
        return result
    }
}
