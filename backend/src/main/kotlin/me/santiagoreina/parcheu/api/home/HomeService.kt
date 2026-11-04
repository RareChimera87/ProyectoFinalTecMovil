package me.santiagoreina.parcheu.api.home

import me.santiagoreina.parcheu.api.platform.Premium
import me.santiagoreina.parcheu.api.quota.QuotaService
import me.santiagoreina.parcheu.api.simulation.SimulationService
import me.santiagoreina.parcheu.contract.HomeDto
import me.santiagoreina.parcheu.contract.SimulationOrigin
import me.santiagoreina.parcheu.contract.SimulationStatus
import java.util.UUID

/**
 * `GET /v1/home` (E4.7): agregador sin lógica de negocio propia. Junta el cupo de [QuotaService], la
 * simulación en curso y los resultados listos de [SimulationService] (E4.3), las sugerencias del
 * grafo (E3.1, [HomeSuggestions]) y el conteo de no leídas.
 */
class HomeService(
    private val quota: QuotaService,
    private val simulations: SimulationService,
    private val suggestions: HomeSuggestions,
    private val unread: UnreadCounter,
) {
    @Premium("GET /v1/home")
    suspend fun home(viewer: UUID): HomeDto {
        val status = quota.status(viewer)
        // La primera página (más recientes primero) basta: la simulación en curso es reciente.
        val recent = simulations.list(viewer, cursor = null, limit = SimulationService.MAX_PAGE).items
        // La evaluación de un escenario (origen SCENARIO) no es una conversación de gemelos: el cierre vive en F3.
        val running = recent.firstOrNull {
            it.origin != SimulationOrigin.SCENARIO && (it.status == SimulationStatus.RUNNING || it.status == SimulationStatus.QUEUED)
        }
        val ready = recent.filter { it.status == SimulationStatus.DONE }.take(MAX_READY)
        // No se sugiere a quien ya está simulando con el usuario.
        val busy = setOfNotNull(running?.other?.id?.let { runCatching { UUID.fromString(it) }.getOrNull() })
        return HomeDto(
            quota = status,
            running = running,
            ready = ready,
            suggestions = suggestions.forHome(viewer, status.plus, busy),
            unread = unread.unread(viewer),
            plus = status.plus,
        )
    }

    companion object {
        /** Resultados listos que muestra C1; el resto está en 'Ver todas'. */
        const val MAX_READY = 2
    }
}
