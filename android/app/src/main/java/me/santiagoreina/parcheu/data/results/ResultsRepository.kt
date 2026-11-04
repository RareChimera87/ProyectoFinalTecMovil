package me.santiagoreina.parcheu.data.results

import me.santiagoreina.parcheu.contract.Decision
import me.santiagoreina.parcheu.contract.DecisionResult
import me.santiagoreina.parcheu.contract.QuotaDto
import me.santiagoreina.parcheu.contract.ReportDto
import me.santiagoreina.parcheu.contract.SimulationDto
import me.santiagoreina.parcheu.contract.TranscriptDto
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall

/**
 * Resultado de una simulación (E4.6). El servidor decide qué ve cada plan: la app pinta lo que
 * llega y nunca recibe las razones ni los choques de un informe bloqueado.
 */
interface ResultsRepository {
    /** `GET /v1/simulations/{id}`: modo y con quién (etiqueta, insignia demo). */
    suspend fun simulation(id: String): ApiResult<SimulationDto>

    /** `GET /v1/simulations/{id}/report`: informe redactado según el plan. */
    suspend fun report(id: String): ApiResult<ReportDto>

    /** `POST .../report:unlock`: gasta el informe completo del mes (402 si ya se usó). */
    suspend fun unlock(id: String): ApiResult<ReportDto>

    /** `POST .../decision`: Conectar (`true`) o Ahora no (`false`). */
    suspend fun decide(id: String, connect: Boolean): ApiResult<DecisionResult>

    /** `GET .../transcript`: la conversación de los gemelos (Plus o informe desbloqueado). */
    suspend fun transcript(id: String): ApiResult<TranscriptDto>

    /** `GET /v1/quota`: si es Plus y cuándo vuelve el informe gratis. */
    suspend fun quota(): ApiResult<QuotaDto>
}

class RemoteResultsRepository(private val api: ResultsApi, private val mapper: ApiErrorMapper) :
    ResultsRepository {
    override suspend fun simulation(id: String) = apiCall(mapper) { api.simulation(id) }

    override suspend fun report(id: String) = apiCall(mapper) { api.report(id) }

    override suspend fun unlock(id: String) = apiCall(mapper) { api.unlock(id) }

    override suspend fun decide(id: String, connect: Boolean) = apiCall(mapper) {
        api.decide(id, Decision(connect))
    }

    override suspend fun transcript(id: String) = apiCall(mapper) { api.transcript(id) }

    override suspend fun quota() = apiCall(mapper) { api.quota() }
}
