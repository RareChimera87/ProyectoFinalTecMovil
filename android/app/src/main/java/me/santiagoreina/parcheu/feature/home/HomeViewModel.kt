package me.santiagoreina.parcheu.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.HomeDto
import me.santiagoreina.parcheu.contract.Suggestion
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.simulation.SimulationRepository
import me.santiagoreina.parcheu.data.simulation.toSnapshot
import me.santiagoreina.parcheu.feature.plus.PlusErrorRouter
import me.santiagoreina.parcheu.feature.simulation.SCENARIO_COUNT
import me.santiagoreina.parcheu.feature.simulation.progressPercent
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.Simulation

/** Origen del paywall cuando se abre desde C1 (analítica de G1). */
const val PAYWALL_SOURCE_HOME = "home"

/** Avance de la simulación en curso que muestra la tarjeta de C1. */
data class RunningProgress(val percent: Int, val scenario: Int)

sealed interface HomeUiState {
    data object Loading : HomeUiState

    /**
     * C1. [firstName] saluda ("¿Qué más, Mari?"); [progress] llega aparte (best effort) desde
     * `GET /v1/simulations/{id}`; [startingId] es la sugerencia que está iniciando simulación.
     */
    data class Loaded(
        val home: HomeDto,
        val firstName: String? = null,
        val progress: RunningProgress? = null,
        val startingId: String? = null,
        val actionError: AppError? = null
    ) : HomeUiState {
        /** Estado vacío de C1: nada en curso, ningún resultado y ninguna sugerencia. */
        val isEmpty: Boolean get() = home.running == null && home.ready.isEmpty() &&
            home.suggestions.isEmpty()
    }

    data class Error(val error: AppError) : HomeUiState
}

sealed interface HomeEffect {
    data class Navigate(val route: AppRoute) : HomeEffect
}

/**
 * C1 / I1 (E4.7). Carga `GET /v1/home` al entrar (y al volver a Inicio, sin parpadeo si ya había datos),
 * pide el avance de la simulación en curso y lanza "Simular" desde una sugerencia.
 */
class HomeViewModel(
    private val repository: HomeRepository,
    private val simulations: SimulationRepository,
    private val displayName: () -> String? = { null }
) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = _effects.receiveAsFlow()

    /** Carga o refresca. Con datos en pantalla refresca en silencio; un error solo reemplaza la carga inicial. */
    fun load() {
        val current = _state.value
        if (current !is HomeUiState.Loaded) _state.value = HomeUiState.Loading
        viewModelScope.launch {
            when (val result = repository.home()) {
                is ApiResult.Failure ->
                    if (current !is HomeUiState.Loaded) {
                        _state.value =
                            HomeUiState.Error(result.error)
                    }
                is ApiResult.Success -> {
                    val loaded = HomeUiState.Loaded(
                        home = result.value,
                        firstName = firstNameOf(displayName())
                    )
                    _state.value = loaded
                    loadProgress(loaded.home)
                }
            }
        }
    }

    private suspend fun loadProgress(home: HomeDto) {
        val running = home.running ?: return
        val detail = (simulations.get(running.id) as? ApiResult.Success)?.value ?: return
        val snapshot = detail.toSnapshot()
        val progress =
            RunningProgress(
                percent = progressPercent(snapshot),
                scenario = (snapshot.progress.scenarioIndex + 1).coerceIn(1, SCENARIO_COUNT)
            )
        update { if (it.home.running?.id == running.id) it.copy(progress = progress) else it }
    }

    /**
     * "Simular" de una sugerencia: inicia y abre F4. Sin cupo o con función premium abre el paywall, salvo
     * para ParcheU+: si ya es Plus y llegó al uso justo diario, el paywall le vendería lo que ya tiene, así
     * que se muestra el aviso en C1.
     */
    fun onSimulate(suggestion: Suggestion) {
        val loaded = _state.value as? HomeUiState.Loaded ?: return
        val otherId = suggestion.other.id ?: return
        if (loaded.startingId != null) return
        update { it.copy(startingId = otherId, actionError = null) }
        viewModelScope.launch {
            when (val result = simulations.start(otherId, suggestion.mode)) {
                is ApiResult.Success -> {
                    update { it.copy(startingId = null) }
                    _effects.send(HomeEffect.Navigate(Simulation(result.value.id)))
                }
                is ApiResult.Failure -> {
                    val error = result.error
                    val plus = (_state.value as? HomeUiState.Loaded)?.home?.plus == true
                    val premiumRoute = PlusErrorRouter.route(error, PAYWALL_SOURCE_HOME)
                    if (!plus && premiumRoute != null) {
                        // PLUS_REQUIRED abre G1; QUOTA_EXCEEDED(kind) abre la hoja G2 de ese cupo (E7.3).
                        update { it.copy(startingId = null) }
                        _effects.send(HomeEffect.Navigate(premiumRoute))
                    } else {
                        update { it.copy(startingId = null, actionError = error) }
                    }
                }
            }
        }
    }

    fun onDismissError() = update { it.copy(actionError = null) }

    private fun update(block: (HomeUiState.Loaded) -> HomeUiState.Loaded) {
        val current = _state.value as? HomeUiState.Loaded ?: return
        _state.value = block(current)
    }
}

/** Primer nombre para el saludo ("Mariana Gómez" -> "Mariana"); null si no hay nombre. */
internal fun firstNameOf(displayName: String?): String? =
    displayName?.trim()?.split(' ')?.firstOrNull { it.isNotBlank() }
