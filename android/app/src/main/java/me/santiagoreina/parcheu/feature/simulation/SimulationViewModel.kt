package me.santiagoreina.parcheu.feature.simulation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.SimulationSummary
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.simulation.SimulationLiveSource
import me.santiagoreina.parcheu.data.simulation.SimulationRepository
import me.santiagoreina.parcheu.data.simulation.SimulationSnapshot
import me.santiagoreina.parcheu.data.simulation.toSnapshot

/** Cantidad de escenarios de una simulación (arquitectura.md §7.2). */
const val SCENARIO_COUNT = 3

/** Tiempo sin datos del listener antes de consultar la API una vez (respaldo). */
val LISTENER_SILENCE_TIMEOUT: Duration = 10.seconds

enum class StepState { DONE, IN_PROGRESS, PENDING }

/** Un renglón de la lista de escenarios de F4. [number] empieza en 1. */
data class ScenarioStep(val number: Int, val state: StepState)

/** Con quién se simula (encabezado de F4): llega con el resumen de la API. */
data class Participants(val otherLabel: String, val mode: Mode, val isDemo: Boolean)

sealed interface SimulationUiState {
    /** Aún sin datos del listener ni de la API. */
    data object Connecting : SimulationUiState

    /** F4. [percent] de 0 a 100. */
    data class Running(
        val participants: Participants?,
        val steps: List<ScenarioStep>,
        val currentScenario: Int,
        val percent: Int
    ) : SimulationUiState

    /**
     * F5. [refunded] solo es `true` si el resumen de la API lo trae: entonces se avisa que no se descontó
     * del cupo. [canRetry] es falso cuando no se puede repetir (la otra persona sigue anónima).
     */
    data class Failed(
        val pausedAtScenario: Int?,
        val refunded: Boolean,
        val canRetry: Boolean,
        val retrying: Boolean = false,
        val retryError: AppError? = null
    ) : SimulationUiState

    /** No hubo forma de saber el estado (sin red y sin datos del listener). */
    data class Error(val error: AppError) : SimulationUiState
}

sealed interface SimulationEffect {
    /** DONE: reemplaza F4 por los resultados. */
    data class OpenResults(val simulationId: String) : SimulationEffect

    /** Reintento aceptado: reemplaza F4/F5 por la nueva simulación. */
    data class OpenSimulation(val simulationId: String) : SimulationEffect
}

/**
 * F4/F5 (E4.5). Escucha `simulations/{id}` (ADR-0004); si en [silenceTimeout] el listener no entrega nada
 * consulta `GET /v1/simulations/{id}` una vez y sigue escuchando. Salir de la pantalla no detiene la
 * simulación: al volver, un ViewModel nuevo se suscribe y recibe el estado actual.
 */
class SimulationViewModel(
    private val simulationId: String,
    private val repository: SimulationRepository,
    private val live: SimulationLiveSource,
    private val silenceTimeout: Duration = LISTENER_SILENCE_TIMEOUT
) : ViewModel() {
    private val _state = MutableStateFlow<SimulationUiState>(SimulationUiState.Connecting)
    val state: StateFlow<SimulationUiState> = _state.asStateFlow()

    private val _effects = Channel<SimulationEffect>(Channel.BUFFERED)
    val effects: Flow<SimulationEffect> = _effects.receiveAsFlow()

    private var summary: SimulationSummary? = null
    private var received = false
    private var finished = false
    private var summaryRequested = false
    private var failedRefetched = false

    init {
        viewModelScope.launch {
            live.observe(simulationId).catch {
                if (!received) fetchFromApi()
            }.collect { onSnapshot(it) }
        }
        viewModelScope.launch {
            delay(silenceTimeout)
            if (!received) fetchFromApi()
        }
    }

    /** "Reintentar" de la pantalla de error de carga (sin datos). */
    fun onReload() {
        _state.value = SimulationUiState.Connecting
        viewModelScope.launch { fetchFromApi() }
    }

    /** "Reintentar" de F5: inicia otra simulación con la misma persona. */
    fun onRetry() {
        val failed = _state.value as? SimulationUiState.Failed ?: return
        val other = summary?.other?.id
        val mode = summary?.mode
        if (failed.retrying || other == null || mode == null) return
        viewModelScope.launch {
            _state.value = failed.copy(retrying = true, retryError = null)
            when (val result = repository.start(other, mode)) {
                is ApiResult.Success ->
                    _effects.send(SimulationEffect.OpenSimulation(result.value.id))
                is ApiResult.Failure ->
                    _state.value = failed.copy(retrying = false, retryError = result.error)
            }
        }
    }

    private suspend fun fetchFromApi() {
        when (val result = repository.get(simulationId)) {
            is ApiResult.Success -> {
                summary = result.value.summary
                onSnapshot(result.value.toSnapshot(), fresh = true)
            }
            is ApiResult.Failure ->
                if (!received && _state.value is SimulationUiState.Connecting) {
                    _state.value = SimulationUiState.Error(result.error)
                }
        }
    }

    private suspend fun onSnapshot(snapshot: SimulationSnapshot, fresh: Boolean = false) {
        received = true
        // El encabezado (con quién y en qué modo) y `refunded` solo existen en la API: se pide el resumen
        // una vez mientras corre y una vez más al fallar (el reembolso se registra junto con FAILED).
        val failed = snapshot.status == SimulationStatus.FAILED
        val needsSummary = summary == null && !summaryRequested
        val needsRefund = failed && summary?.refunded != true && !failedRefetched
        if (!fresh && (needsSummary || needsRefund)) {
            summaryRequested = true
            if (failed) failedRefetched = true
            loadSummary()
        }
        render(snapshot)
    }

    private suspend fun loadSummary() {
        (repository.get(simulationId) as? ApiResult.Success)?.let { summary = it.value.summary }
    }

    private suspend fun render(snapshot: SimulationSnapshot) {
        when (snapshot.status) {
            SimulationStatus.DONE -> {
                if (!finished) {
                    finished = true
                    _effects.send(SimulationEffect.OpenResults(simulationId))
                }
            }
            SimulationStatus.FAILED -> {
                val previous = _state.value as? SimulationUiState.Failed
                _state.value =
                    SimulationUiState.Failed(
                        pausedAtScenario = snapshot.progress.scenarioIndex.coerceAtLeast(0) + 1,
                        refunded = summary?.refunded == true,
                        canRetry = summary?.other?.id != null,
                        retrying = previous?.retrying == true,
                        retryError = previous?.retryError
                    )
            }
            SimulationStatus.QUEUED,
            SimulationStatus.RUNNING -> {
                val current = snapshot.progress.scenarioIndex.coerceIn(0, SCENARIO_COUNT - 1)
                _state.value =
                    SimulationUiState.Running(
                        participants = summary?.let {
                            Participants(it.other.label, it.mode, it.other.isDemo)
                        },
                        steps = scenarioSteps(snapshot),
                        currentScenario = current + 1,
                        percent = progressPercent(snapshot)
                    )
            }
        }
    }
}

/** Lista de 3 escenarios: los anteriores al actual están listos, el actual en curso y el resto pendiente. */
fun scenarioSteps(snapshot: SimulationSnapshot): List<ScenarioStep> {
    val current = snapshot.progress.scenarioIndex.coerceAtLeast(0)
    return (0 until SCENARIO_COUNT).map { index ->
        val state =
            when {
                snapshot.status == SimulationStatus.DONE || index < current -> StepState.DONE
                index == current -> StepState.IN_PROGRESS
                else -> StepState.PENDING
            }
        ScenarioStep(number = index + 1, state = state)
    }
}

/** Avance total: escenarios completos más la fracción de turnos del actual, sobre [SCENARIO_COUNT]. */
fun progressPercent(snapshot: SimulationSnapshot): Int {
    if (snapshot.status == SimulationStatus.DONE) return 100
    val progress = snapshot.progress
    val turnFraction =
        if (progress.of > 0) (progress.turn.toFloat() / progress.of).coerceIn(0f, 1f) else 0f
    val done = progress.scenarioIndex.coerceIn(0, SCENARIO_COUNT) + turnFraction
    return (done / SCENARIO_COUNT * 100).roundToInt().coerceIn(0, 100)
}
