package me.santiagoreina.parcheu.feature.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

/** Paso del flujo de H1. */
enum class SheetStep {
    /** H1: Silenciar, Reportar, Bloquear. */
    Menu,

    /** Confirmación antes de bloquear (nueva, no está en los mockups). */
    ConfirmBlock,

    /** Bloqueo hecho: confirmación (nueva). */
    Blocked,

    /** La hoja se cerró; [SafetySheetState.closedBy] dice por qué. */
    Closed
}

/** Por qué se cerró la hoja: la anfitriona reacciona (abrir H2, salir del chat, actualizar el silencio). */
enum class CloseReason { Dismissed, Muted, Report, Blocked }

data class SafetySheetState(
    val target: SafetyTarget,
    val step: SheetStep = SheetStep.Menu,
    val muted: Boolean = target.muted,
    val busy: Boolean = false,
    val error: AppError? = null,
    val closedBy: CloseReason? = null
)

/**
 * H1 (E8.1): la hoja de reportar o bloquear y sus confirmaciones. Silenciar llama `POST /v1/chats/{id}/mute`
 * y cierra la hoja sin bloquear; bloquear pide confirmación y luego llama `POST /v1/blocks/{id}`. Reportar
 * solo cierra la hoja: la pantalla anfitriona abre H2 (ruta `Report`).
 */
class SafetySheetViewModel(target: SafetyTarget, private val repository: SafetyRepository) :
    ViewModel() {
    private val _state = MutableStateFlow(SafetySheetState(target))
    val state: StateFlow<SafetySheetState> = _state.asStateFlow()

    /** Silenciar o quitar el silencio del chat. Sin chat no hay nada que silenciar. */
    fun toggleMute() {
        val current = _state.value
        val chatId = current.target.chatId ?: return
        if (current.busy) return
        val target = !current.muted
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.setMuted(chatId, target)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        busy = false,
                        muted = target,
                        step = SheetStep.Closed,
                        closedBy = CloseReason.Muted
                    )
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(busy = false, error = result.error)
                }
            }
        }
    }

    /** "Reportar a …": la hoja se cierra y la anfitriona navega a H2. */
    fun report() {
        _state.update {
            it.copy(step = SheetStep.Closed, error = null, closedBy = CloseReason.Report)
        }
    }

    fun requestBlock() {
        _state.update { it.copy(step = SheetStep.ConfirmBlock, error = null) }
    }

    fun cancelBlock() {
        _state.update { it.copy(step = SheetStep.Menu, error = null, busy = false) }
    }

    fun confirmBlock() {
        val current = _state.value
        if (current.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.block(current.target)) {
                is ApiResult.Success -> _state.update {
                    it.copy(busy = false, step = SheetStep.Blocked)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(busy = false, error = result.error)
                }
            }
        }
    }

    /** Cerrar la hoja (scrim, atrás) o, tras bloquear, "Entendido". */
    fun dismiss() {
        _state.update {
            val reason = if (it.step ==
                SheetStep.Blocked
            ) {
                CloseReason.Blocked
            } else {
                CloseReason.Dismissed
            }
            it.copy(step = SheetStep.Closed, busy = false, closedBy = reason)
        }
    }
}
