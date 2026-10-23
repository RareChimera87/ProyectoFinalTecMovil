package me.santiagoreina.parcheu.feature.conocer.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.MatchStatus
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
    /** Modos que el usuario puede elegir (los que tiene activos). */
    val activeModes: List<Mode> = Mode.entries,
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
class MatchQueueViewModel(
    initialMode: Mode?,
    private val repository: MatchRepository,
    activeModes: List<Mode> = Mode.entries,
    private val pollIntervalMs: Long = POLL_INTERVAL_MS
) : ViewModel() {
    private val _state = MutableStateFlow(
        MatchQueueUiState(
            mode = initialMode
                ?: repository.lastMode?.takeIf { it in activeModes }
                ?: activeModes.firstOrNull()
                ?: Mode.AMISTAD,
            activeModes = activeModes
        )
    )
    val state: StateFlow<MatchQueueUiState> = _state.asStateFlow()

    private val _effects = Channel<MatchQueueEffect>(Channel.BUFFERED)
    val effects: Flow<MatchQueueEffect> = _effects.receiveAsFlow()

    private var searchJob: Job? = null
    private var opened = false

    init {
        search(cancelPrevious = false)
    }

    companion object {
        /** Cada cuánto se consulta `GET /v1/match/queue` si el listener de Firestore falla. */
        const val POLL_INTERVAL_MS = 10_000L
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

    /**
     * Entra a la fila. Si [cancelPrevious] es falso primero reanuda un ticket vivo
     * (`GET /v1/match/queue`), por ejemplo tras morir la app o perderse la respuesta del POST.
     */
    private fun search(cancelPrevious: Boolean) {
        searchJob?.cancel()
        opened = false
        _state.update { it.copy(phase = QueuePhase.Searching, limitSheet = null) }
        searchJob = viewModelScope.launch {
            if (cancelPrevious) {
                repository.cancel()
            } else if (resumeLiveTicket()) {
                return@launch
            }
            when (val result = repository.enqueue(_state.value.mode)) {
                is ApiResult.Failure -> {
                    // 409: ya estás en la fila (la respuesta del POST anterior se perdió).
                    if (result.error == AppError.Conflict && resumeLiveTicket()) return@launch
                    onEnqueueFailed(result.error)
                }
                is ApiResult.Success -> follow(result.value)
            }
        }
    }

    /**
     * Reanuda el ticket del servidor; `true` si había uno vivo. `WAITING` siempre se reanuda.
     * `MATCHED` es el estado terminal de la fila (el GET lo sigue devolviendo tras el match), así
     * que solo se reanuda si el match aún espera elección propia (`PENDING_CHOICE` sin `myChoice`);
     * si venció, ya se eligió o está en simulación, se encola de nuevo.
     */
    private suspend fun resumeLiveTicket(): Boolean {
        val ticket = (repository.currentTicket() as? ApiResult.Success)?.value ?: return false
        when (ticket.status) {
            TicketStatus.EXPIRED -> return false
            TicketStatus.WAITING -> Unit
            TicketStatus.MATCHED -> {
                val id = ticket.matchId ?: return false
                val match = (repository.match(id) as? ApiResult.Success)?.value ?: return false
                if (match.status != MatchStatus.PENDING_CHOICE ||
                    match.myChoice != null
                ) {
                    return false
                }
            }
        }
        _state.update { it.copy(mode = ticket.mode) }
        follow(ticket)
        return true
    }

    private suspend fun follow(ticket: TicketDto) {
        onTicket(ticket)
        repository.observeTicket()
            .catch { pollTicket() }
            .collect { latest -> latest?.let { onTicket(it) } }
    }

    /** Respaldo si el listener falla: consulta el ticket hasta que deje de estar `WAITING`. */
    private suspend fun pollTicket() {
        while (_state.value.phase == QueuePhase.Searching) {
            delay(pollIntervalMs)
            (repository.currentTicket() as? ApiResult.Success)?.value?.let { onTicket(it) }
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
        // Un ticket de otro modo es de una fila anterior (cambio de modo en curso).
        if (ticket.mode != _state.value.mode) return
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
