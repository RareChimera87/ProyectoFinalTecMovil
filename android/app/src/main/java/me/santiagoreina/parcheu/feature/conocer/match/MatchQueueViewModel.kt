package me.santiagoreina.parcheu.feature.conocer.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.contract.TicketDto
import me.santiagoreina.parcheu.contract.TicketStatus
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

/** Fase de F1. */
sealed interface QueuePhase {
    /** Buscando (ticket `WAITING` o POST en curso). */
    data object Searching : QueuePhase

    /** Ticket `MATCHED`: F2 ya se abrió o está por abrirse. */
    data class Matched(val matchId: String) : QueuePhase

    /** El ticket venció a las 24 h sin match. */
    data object Expired : QueuePhase

    data class Error(val error: AppError) : QueuePhase
}

/** Hoja de límite de match diario (G2 de RANDOM_MATCH, que el inventario no diseñó aparte). */
data class MatchLimitSheet(val resetsAt: Instant?)

data class MatchQueueUiState(
    val mode: Mode,
    val phase: QueuePhase = QueuePhase.Searching,
    val limitSheet: MatchLimitSheet? = null
)

sealed interface MatchQueueEffect {
    /** Abre F2. */
    data class OpenMatch(val matchId: String) : MatchQueueEffect

    /** Sale de F1 (cancelar, cerrar la hoja de límite). */
    data object Close : MatchQueueEffect

    /** Abre G1 desde la hoja de límite. */
    data object OpenPaywall : MatchQueueEffect
}

/**
 * F1 (E4.4): entra a la fila con `POST /v1/match/queue` y escucha `matchTickets/{uid}`; al pasar a
 * `MATCHED` abre F2. Cambiar de modo cancela la fila y entra a la del modo nuevo (no cobra nada).
 */
class MatchQueueViewModel(initialMode: Mode?, private val repository: MatchRepository) :
    ViewModel() {
    private val _state = MutableStateFlow(MatchQueueUiState(mode = initialMode ?: Mode.AMISTAD))
    val state: StateFlow<MatchQueueUiState> = _state.asStateFlow()

    private val _effects = Channel<MatchQueueEffect>(Channel.BUFFERED)
    val effects: Flow<MatchQueueEffect> = _effects.receiveAsFlow()

    private var searchJob: Job? = null
    private var opened = false

    init {
        search(cancelPrevious = false)
    }

    fun onModeSelected(mode: Mode) {
        if (mode == _state.value.mode) return
        _state.update { it.copy(mode = mode) }
        search(cancelPrevious = true)
    }

    fun onRetry() = search(cancelPrevious = false)

    /** "Cancelar búsqueda": `DELETE /v1/match/queue` y vuelve a Inicio. */
    fun onCancel() {
        searchJob?.cancel()
        viewModelScope.launch {
            when (val result = repository.cancel()) {
                is ApiResult.Success -> _effects.send(MatchQueueEffect.Close)
                is ApiResult.Failure -> _state.update {
                    it.copy(phase = QueuePhase.Error(result.error))
                }
            }
        }
    }

    fun onOpenMatchAgain() {
        (_state.value.phase as? QueuePhase.Matched)?.let {
            viewModelScope.launch { _effects.send(MatchQueueEffect.OpenMatch(it.matchId)) }
        }
    }

    fun onLimitDismissed() {
        _state.update { it.copy(limitSheet = null) }
        viewModelScope.launch { _effects.send(MatchQueueEffect.Close) }
    }

    fun onLimitSeePlus() {
        _state.update { it.copy(limitSheet = null) }
        viewModelScope.launch { _effects.send(MatchQueueEffect.OpenPaywall) }
    }

    private fun search(cancelPrevious: Boolean) {
        searchJob?.cancel()
        opened = false
        _state.update { it.copy(phase = QueuePhase.Searching, limitSheet = null) }
        searchJob = viewModelScope.launch {
            if (cancelPrevious) repository.cancel()
            when (val result = repository.enqueue(_state.value.mode)) {
                is ApiResult.Failure -> onEnqueueFailed(result.error)
                is ApiResult.Success -> {
                    onTicket(result.value)
                    repository.observeTicket().collect { ticket -> ticket?.let { onTicket(it) } }
                }
            }
        }
    }

    private fun onEnqueueFailed(error: AppError) {
        if (error is AppError.QuotaExceeded && error.kind == QuotaKind.RANDOM_MATCH) {
            _state.update { it.copy(limitSheet = MatchLimitSheet(error.resetsAt)) }
        } else {
            _state.update { it.copy(phase = QueuePhase.Error(error)) }
        }
    }

    private suspend fun onTicket(ticket: TicketDto) {
        when (ticket.status) {
            TicketStatus.WAITING -> _state.update { it.copy(phase = QueuePhase.Searching) }
            TicketStatus.EXPIRED -> _state.update { it.copy(phase = QueuePhase.Expired) }
            TicketStatus.MATCHED -> {
                val id = ticket.matchId ?: return
                _state.update { it.copy(phase = QueuePhase.Matched(id)) }
                if (!opened) {
                    opened = true
                    _effects.send(MatchQueueEffect.OpenMatch(id))
                }
            }
        }
    }
}
