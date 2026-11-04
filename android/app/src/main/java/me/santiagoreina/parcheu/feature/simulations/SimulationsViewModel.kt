package me.santiagoreina.parcheu.feature.simulations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.SimulationSummary
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.simulation.SimulationRepository

sealed interface SimulationsUiState {
    data object Loading : SimulationsUiState

    /** F6. [available] = simulaciones que quedan en la semana; `null` si no se pudo saber o son ilimitadas. */
    data class Empty(val available: Int?) : SimulationsUiState

    data class Loaded(
        val items: List<SimulationSummary>,
        val nextCursor: String?,
        val loadingMore: Boolean = false
    ) : SimulationsUiState

    data class Error(val error: AppError) : SimulationsUiState
}

/** F6 y 'Ver todas las simulaciones' (E4.5): primera página, paginación por cursor y el vacío con el cupo. */
class SimulationsViewModel(private val repository: SimulationRepository) : ViewModel() {
    private val _state = MutableStateFlow<SimulationsUiState>(SimulationsUiState.Loading)
    val state: StateFlow<SimulationsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = SimulationsUiState.Loading
        viewModelScope.launch {
            when (val result = repository.list(cursor = null)) {
                is ApiResult.Failure -> _state.value = SimulationsUiState.Error(result.error)
                is ApiResult.Success ->
                    _state.value =
                        if (result.value.items.isEmpty()) {
                            SimulationsUiState.Empty(available = remainingThisWeek())
                        } else {
                            SimulationsUiState.Loaded(result.value.items, result.value.nextCursor)
                        }
            }
        }
    }

    fun loadMore() {
        val current = _state.value as? SimulationsUiState.Loaded ?: return
        val cursor = current.nextCursor ?: return
        if (current.loadingMore) return
        _state.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            when (val result = repository.list(cursor)) {
                is ApiResult.Failure -> _state.value = current.copy(loadingMore = false)
                is ApiResult.Success ->
                    _state.value =
                        SimulationsUiState.Loaded(
                            items = current.items + result.value.items,
                            nextCursor = result.value.nextCursor
                        )
            }
        }
    }

    private suspend fun remainingThisWeek(): Int? {
        val quota = (repository.quota() as? ApiResult.Success)?.value ?: return null
        val limit = quota.simulations.limit ?: return null
        return (limit - quota.simulations.used).coerceAtLeast(0)
    }
}
