package me.santiagoreina.parcheu.feature.evolution

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.HistoryDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.Paywall

/** Origen del paywall cuando se abre desde D9 (analítica de G1). */
const val PAYWALL_SOURCE_EVOLUTION = "evolution"

/** Meses mínimos para dibujar la línea de tiempo (menos: estado "< 2 meses" de D9). */
const val MIN_MONTHS = 2

/** Estados de D9: cargando, error, bloqueado (gratis), con poco historial y con historial. */
sealed interface EvolutionUiState {
    data object Loading : EvolutionUiState

    data class Error(val error: AppError) : EvolutionUiState

    /** Gratis: el servidor respondió 402 y no mandó ningún dato. */
    data object Locked : EvolutionUiState

    /** ParcheU+ con menos de [MIN_MONTHS] meses de historia. */
    data class TooEarly(val history: HistoryDto) : EvolutionUiState

    /** D9 con historial. [selected] es el mes (`yyyy-MM`) que la persona tocó. */
    data class Content(val history: HistoryDto, val selected: String? = null) : EvolutionUiState
}

/** D9 (E6.5). Gratis ve el bloqueo y "Ver ParcheU+" abre G1; ParcheU+ ve meses y momentos. */
class EvolutionViewModel(private val repository: EvolutionRepository) : ViewModel() {
    private val _state = MutableStateFlow<EvolutionUiState>(EvolutionUiState.Loading)
    val state: StateFlow<EvolutionUiState> = _state.asStateFlow()

    private val _navigation = Channel<AppRoute>(Channel.BUFFERED)
    val navigation: Flow<AppRoute> = _navigation.receiveAsFlow()

    private var loadJob: Job? = null
    private var started = false

    /** Primera carga; repetirla no hace nada. */
    fun start() {
        if (started) return
        started = true
        load()
    }

    fun retry() {
        _state.value = EvolutionUiState.Loading
        load()
    }

    /** Volver de G1 con ParcheU+ recién comprado: recarga sin spinner si seguía bloqueado. */
    fun refreshIfLocked() {
        if (_state.value == EvolutionUiState.Locked) load()
    }

    /** Toca un mes; tocarlo otra vez lo deselecciona. */
    fun onMonth(month: String) {
        val current = _state.value as? EvolutionUiState.Content ?: return
        _state.value = current.copy(selected = month.takeUnless { it == current.selected })
    }

    fun onUnlock() {
        viewModelScope.launch { _navigation.send(Paywall(PAYWALL_SOURCE_EVOLUTION)) }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _state.value =
                    when (val result = repository.history()) {
                        is ApiResult.Failure ->
                            if (result.error == AppError.PlusRequired) {
                                EvolutionUiState.Locked
                            } else {
                                EvolutionUiState.Error(result.error)
                            }
                        is ApiResult.Success ->
                            if (result.value.months.size < MIN_MONTHS) {
                                EvolutionUiState.TooEarly(result.value)
                            } else {
                                EvolutionUiState.Content(result.value)
                            }
                    }
            }
    }
}

/** Mensaje del error de carga de D9. */
fun AppError.evolutionMessage(): String = when (this) {
    AppError.Network -> "Revisa tu conexión e intenta de nuevo."
    else -> "Algo salió mal. Intenta de nuevo en un momento."
}
