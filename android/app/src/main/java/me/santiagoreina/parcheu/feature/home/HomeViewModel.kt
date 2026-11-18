package me.santiagoreina.parcheu.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
    private val displayName: () -> String? = { null },
    /** Reloj y cadencia del refresco silencioso; inyectables para pruebas. */
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val pollIntervalMs: Long = POLL_INTERVAL_MS
) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = _effects.receiveAsFlow()

    private var loadJob: Job? = null
    private var pollJob: Job? = null
    private var lastLoadAtMs: Long? = null
    private var resumed = false

    /**
     * Carga o refresca. Con datos en pantalla refresca en silencio; un error solo reemplaza la carga inicial.
     * No solapa: si ya hay una carga en vuelo no hace nada.
     */
    fun load() {
        if (loadJob?.isActive == true) return
        val current = _state.value
        if (current !is HomeUiState.Loaded) _state.value = HomeUiState.Loading
        loadJob =
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
                lastLoadAtMs = nowMs()
                startPolling()
            }
    }

    /**
     * Cada ON_RESUME de la ruta (también al volver de otra pestaña, donde la composición se recrea pero este
     * ViewModel sobrevive): recarga en silencio si la última carga terminó hace más de
     * [MIN_REFRESH_INTERVAL_MS], y mientras esté a la vista sondea si hay una simulación en curso.
     */
    fun onResumed() {
        resumed = true
        startPolling()
        val last = lastLoadAtMs ?: return
        if (_state.value !is HomeUiState.Loaded) return
        if (nowMs() - last < MIN_REFRESH_INTERVAL_MS) return
        load()
    }

    /** Inicio sale de primer plano: se detiene el sondeo. */
    fun onPaused() {
        resumed = false
        pollJob?.cancel()
        pollJob = null
    }

    /** Un solo sondeo vivo; termina solo cuando ya no hay simulación en curso y nunca muestra errores. */
    private fun startPolling() {
        if (!resumed || pollJob?.isActive == true || !hasRunning()) return
        pollJob =
            viewModelScope.launch {
                while (hasRunning()) {
                    delay(pollIntervalMs)
                    // La carga anterior pudo terminar la simulación mientras esperábamos.
                    if (!hasRunning()) break
                    load()
                }
            }
    }

    private fun hasRunning() = (_state.value as? HomeUiState.Loaded)?.home?.running != null

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
     * "Simular" de una sugerencia: inicia y abre F4. Sin cupo o con función premium el bus global abre G1
     * o la G2 del cupo (E7.3; con ParcheU+ la G2 es la de uso justo diario), así que C1 no muestra error.
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
                    val shown = error.takeUnless(PlusErrorRouter::isPremium)
                    update { it.copy(startingId = null, actionError = shown) }
                }
            }
        }
    }

    fun onDismissError() = update { it.copy(actionError = null) }

    companion object {
        const val MIN_REFRESH_INTERVAL_MS = 3_000L
        const val POLL_INTERVAL_MS = 5_000L
    }

    private fun update(block: (HomeUiState.Loaded) -> HomeUiState.Loaded) {
        val current = _state.value as? HomeUiState.Loaded ?: return
        _state.value = block(current)
    }
}

/** Primer nombre para el saludo ("Mariana Gómez" -> "Mariana"); null si no hay nombre. */
internal fun firstNameOf(displayName: String?): String? =
    displayName?.trim()?.split(' ')?.firstOrNull { it.isNotBlank() }
