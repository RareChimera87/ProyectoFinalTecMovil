package me.santiagoreina.parcheu.data.simulation

import kotlinx.coroutines.flow.Flow
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Page
import me.santiagoreina.parcheu.contract.Progress
import me.santiagoreina.parcheu.contract.QuotaDto
import me.santiagoreina.parcheu.contract.SimulationDto
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.SimulationSummary
import me.santiagoreina.parcheu.contract.StartSimulation
import me.santiagoreina.parcheu.core.error.ApiErrorMapper
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.network.apiCall

/** Lo que lee la app de una simulación: REST (fuente de verdad) y el canal en vivo de Firestore. */
interface SimulationRepository {
    /** `GET /v1/simulations/{id}`: resumen, progreso y puntaje. */
    suspend fun get(id: String): ApiResult<SimulationDto>

    /** `GET /v1/simulations?cursor=`: la lista de "Ver todas las simulaciones". */
    suspend fun list(cursor: String?): ApiResult<Page<SimulationSummary>>

    /** `POST /v1/simulations`: inicia otra simulación con la misma persona (Reintentar de F5). */
    suspend fun start(otherUserId: String, mode: Mode): ApiResult<SimulationSummary>

    /** `GET /v1/quota`: cuántas simulaciones quedan (F6 vacío). */
    suspend fun quota(): ApiResult<QuotaDto>
}

class RemoteSimulationRepository(private val api: SimulationApi, private val mapper: ApiErrorMapper) :
    SimulationRepository {
    override suspend fun get(id: String) = apiCall(mapper) { api.simulation(id) }

    override suspend fun list(cursor: String?) = apiCall(mapper) { api.simulations(cursor) }

    override suspend fun start(otherUserId: String, mode: Mode) =
        apiCall(mapper) { api.start(StartSimulation(otherUserId, mode)) }

    override suspend fun quota() = apiCall(mapper) { api.quota() }
}

/** Estado de `simulations/{id}` en Firestore (arquitectura.md §8.2): lo que cambia durante la corrida. */
data class SimulationSnapshot(val status: SimulationStatus, val progress: Progress, val score: Int?)

/** Convierte la respuesta REST al mismo formato que entrega el canal en vivo. */
fun SimulationDto.toSnapshot() = SimulationSnapshot(summary.status, progress, score ?: summary.score)

/**
 * Canal en vivo de `simulations/{id}` (ADR-0004). Cada colección emite el estado actual al suscribirse, así
 * que salir de F4 y volver reanuda sin perder nada. Puede terminar con error (reglas, red): quien lo
 * consume debe tener el respaldo REST.
 */
interface SimulationLiveSource {
    fun observe(id: String): Flow<SimulationSnapshot>
}
