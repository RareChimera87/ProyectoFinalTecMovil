package me.santiagoreina.parcheu.feature.conocer.match

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.contract.MatchDto
import me.santiagoreina.parcheu.contract.MatchStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

sealed interface MatchFoundUiState {
    data object Loading : MatchFoundUiState

    data class Error(val error: AppError) : MatchFoundUiState

    /**
     * El match ya se resolvió (simulación o escenario) y F2 se abrió sin haber elegido en esta pantalla. La
     * ruta ya navega al destino; este estado evita quedarse en "Cargando…" si regresa a F2.
     */
    data object Resolved : MatchFoundUiState

    /**
     * Match cargado. [selected] es la tarjeta marcada; [submitting] mientras va la elección; [waitingOther]
     * cuando ya elegí y falta la otra persona; [expired] si nadie eligió a tiempo.
     */
    data class Loaded(
        val match: MatchDto,
        val selected: MatchChoice,
        val submitting: Boolean = false,
        val waitingOther: Boolean = false,
        val expired: Boolean = false,
        val actionError: AppError? = null
    ) : MatchFoundUiState {
        /** La tarjeta Escenario solo se ofrece si el backend la habilita (`scenarioAvailable`). */
        val showScenario: Boolean get() = match.scenarioAvailable
    }
}

sealed interface MatchFoundEffect {
    /** F3: el chat SCENARIO del match (`MatchDto.chatId`, E4.8). */
    data class OpenScenario(val chatId: String) : MatchFoundEffect

    /** F4 (la simulación del match se resuelve por el id del match). */
    data class OpenSimulation(val matchId: String) : MatchFoundEffect

    /** Vuelve a F1 para buscar de nuevo. */

    /** Vuelve a F1 con el modo del match que venció. */
    data class SearchAgain(val mode: Mode) : MatchFoundEffect

    data object Close : MatchFoundEffect
}

/**
 * F2 (E4.4): perfil anónimo del match y elección Escenario o Simulación (`POST /v1/matches/{id}/choice`).
 * Si la otra persona aún no elige, consulta el match cada [pollIntervalMs] hasta que cambie de estado.
 */
class MatchFoundViewModel(
    private val matchId: String,
    private val repository: MatchRepository,
    private val pollIntervalMs: Long = POLL_INTERVAL_MS
) : ViewModel() {
    private val _state = MutableStateFlow<MatchFoundUiState>(MatchFoundUiState.Loading)
    val state: StateFlow<MatchFoundUiState> = _state.asStateFlow()

    private val _effects = Channel<MatchFoundEffect>(Channel.BUFFERED)
    val effects: Flow<MatchFoundEffect> = _effects.receiveAsFlow()

    private var pollJob: Job? = null
    private var recheckJob: Job? = null

    init {
        load()
    }

    fun load() {
        _state.value = MatchFoundUiState.Loading
        viewModelScope.launch {
            when (val result = repository.match(matchId)) {
                is ApiResult.Failure -> _state.value = MatchFoundUiState.Error(result.error)
                is ApiResult.Success -> apply(result.value, selected = null)
            }
        }
    }

    /**
     * F2 vuelve a primer plano. Con las tarjetas a la vista se vuelve a consultar el match: si ya no espera
     * mi elección (se resolvió por otra ruta) pasa a [MatchFoundUiState.Resolved] sin navegar, y nunca se
     * ofrece "Empezar" sobre él. Si falla la consulta, o mientras se envía la elección, no cambia nada.
     */
    fun onResumed() {
        val loaded = _state.value as? MatchFoundUiState.Loaded ?: return
        if (loaded.expired || loaded.submitting || recheckJob?.isActive == true) return
        recheckJob = viewModelScope.launch {
            val match = (repository.match(matchId) as? ApiResult.Success)?.value ?: return@launch
            val current = _state.value as? MatchFoundUiState.Loaded ?: return@launch
            if (current.submitting) return@launch
            when (match.status) {
                MatchStatus.SIMULATION, MatchStatus.SCENARIO -> {
                    pollJob?.cancel()
                    _state.value = MatchFoundUiState.Resolved
                }
                MatchStatus.EXPIRED -> apply(match, selected = null)
                // Sigue esperando elección; si ya elegí y falta la otra persona, retoma el sondeo.
                MatchStatus.PENDING_CHOICE ->
                    if (match.myChoice != null && !current.waitingOther) apply(match, null)
            }
        }
    }

    fun onSelect(choice: MatchChoice) {
        val loaded = _state.value as? MatchFoundUiState.Loaded ?: return
        if (loaded.submitting || loaded.waitingOther) return
        if (choice == MatchChoice.SCENARIO && !loaded.showScenario) return
        _state.value = loaded.copy(selected = choice, actionError = null)
    }

    /** "Empezar". */
    fun onStart() {
        val loaded = _state.value as? MatchFoundUiState.Loaded ?: return
        if (loaded.submitting || loaded.waitingOther) return
        _state.value = loaded.copy(submitting = true, actionError = null)
        viewModelScope.launch {
            when (val result = repository.choose(matchId, loaded.selected)) {
                is ApiResult.Success -> apply(result.value, loaded.selected)
                is ApiResult.Failure ->
                    if (result.error == AppError.Conflict) {
                        onChoiceAlreadyRegistered(loaded)
                    } else {
                        showActionError(result.error)
                    }
            }
        }
    }

    /**
     * 409: el servidor ya tiene una elección (p. ej. la respuesta anterior se perdió por la red). Se consulta
     * el match y, si ya se resolvió, se sigue como si la elección hubiera salido bien; si no se puede
     * confirmar, se muestra el error.
     */
    private suspend fun onChoiceAlreadyRegistered(loaded: MatchFoundUiState.Loaded) {
        val match = (repository.match(matchId) as? ApiResult.Success)?.value
        if (match != null &&
            (match.status != MatchStatus.PENDING_CHOICE || match.myChoice != null)
        ) {
            apply(match, loaded.selected)
        } else {
            showActionError(AppError.Conflict)
        }
    }

    private fun showActionError(error: AppError) = _state.update {
        (it as? MatchFoundUiState.Loaded)?.copy(submitting = false, actionError = error) ?: it
    }

    fun onSearchAgain() {
        val loaded = _state.value as? MatchFoundUiState.Loaded ?: return
        viewModelScope.launch { _effects.send(MatchFoundEffect.SearchAgain(loaded.match.mode)) }
    }

    fun onClose() {
        viewModelScope.launch { _effects.send(MatchFoundEffect.Close) }
    }

    private suspend fun apply(match: MatchDto, selected: MatchChoice?) {
        val choice = match.myChoice ?: selected ?: defaultChoice(match)
        when (match.status) {
            MatchStatus.SIMULATION -> {
                markResolved()
                _effects.send(MatchFoundEffect.OpenSimulation(match.id))
            }
            MatchStatus.SCENARIO -> {
                markResolved()
                _effects.send(MatchFoundEffect.OpenScenario(match.chatId ?: match.id))
            }
            MatchStatus.EXPIRED ->
                _state.value = MatchFoundUiState.Loaded(match, choice, expired = true)
            MatchStatus.PENDING_CHOICE -> {
                val waiting = match.myChoice != null
                _state.value = MatchFoundUiState.Loaded(match, choice, waitingOther = waiting)
                if (waiting) startPolling()
            }
        }
    }

    /**
     * El match ya se resolvió. Con las tarjetas a la vista (elegí aquí) solo se apaga "Empezando…"; si F2
     * aún no cargaba nada (abierta sobre un match resuelto) pasa a [MatchFoundUiState.Resolved] en vez de
     * quedarse en "Cargando…".
     */
    private fun markResolved() {
        _state.update {
            (it as? MatchFoundUiState.Loaded)?.copy(submitting = false)
                ?: MatchFoundUiState.Resolved
        }
    }

    private fun defaultChoice(match: MatchDto) =
        if (match.scenarioAvailable) MatchChoice.SCENARIO else MatchChoice.SIMULATION

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                delay(pollIntervalMs)
                val result = repository.match(matchId)
                if (result is ApiResult.Success) {
                    apply(result.value, selected = null)
                    if (result.value.status != MatchStatus.PENDING_CHOICE) break
                }
            }
        }
    }

    companion object {
        const val POLL_INTERVAL_MS = 3_000L
    }
}
