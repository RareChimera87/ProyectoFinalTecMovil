package me.santiagoreina.parcheu.feature.invites

import me.santiagoreina.parcheu.contract.InviteDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Redeem
import me.santiagoreina.parcheu.contract.RedeemResult
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall

/** Mi código, canjear el de otra persona y aceptar canjes. Las pruebas usan
* `FakeInvitesRepository`. */
interface InvitesRepository {
    suspend fun myInvite(): ApiResult<InviteDto>

    suspend fun redeem(code: String, mode: Mode): ApiResult<RedeemResult>

    suspend fun accept(id: String): ApiResult<RedeemResult>
}

class InvitesRepositoryImpl(private val api: InvitesApi, private val errorMapper: ApiErrorMapper) :
    InvitesRepository {
    override suspend fun myInvite() = apiCall(errorMapper) { api.me() }

    override suspend fun redeem(code: String, mode: Mode) =
        apiCall(errorMapper) { api.redeem(Redeem(code, mode)) }

    override suspend fun accept(id: String) = apiCall(errorMapper) { api.accept(id) }
}
