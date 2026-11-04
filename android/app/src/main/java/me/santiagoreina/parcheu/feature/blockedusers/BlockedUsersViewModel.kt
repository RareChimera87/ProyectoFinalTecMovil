package me.santiagoreina.parcheu.feature.blockedusers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.BlockedDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.ui.UiState
import me.santiagoreina.parcheu.feature.safety.SafetyRepository

/**
 * Estado de 'Personas bloqueadas'. [list] es la lista (o su vacío, carga o error); [unblocking] es el id de
 * quien se está desbloqueando y [actionError] el fallo del último intento.
 */
data class BlockedUsersState(
    val list: UiState<List<BlockedDto>> = UiState.Loading,
    val unblocking: String? = null,
    val actionError: AppError? = null
)

/**
 * 'Personas bloqueadas' (pantalla nueva desde H3, E8.7): `GET /v1/blocks` y 'Desbloquear'
 * (`DELETE /v1/blocks/{userId}`). Ambas son gratis y nunca dependen del plan ni del cupo. Desbloquear no
 * restaura conexiones ni chats: el servidor ya los cerró al bloquear.
 */
class BlockedUsersViewModel(private val repository: SafetyRepository) : ViewModel() {
    private val _state = MutableStateFlow(BlockedUsersState())
    val state: StateFlow<BlockedUsersState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            when (val result = repository.blocks()) {
                is ApiResult.Success -> _state.update {
                    it.copy(list = listOrEmpty(result.value), actionError = null)
                }
                is ApiResult.Failure -> _state.update {
                    // Una recarga fallida no borra la lista que ya se ve.
                    if (it.list is UiState.Loaded) {
                        it.copy(actionError = result.error)
                    } else {
                        it.copy(list = UiState.Error(result.error))
                    }
                }
            }
        }
    }

    fun retry() {
        _state.update { it.copy(list = UiState.Loading, actionError = null) }
        load()
    }

    /** Quita el bloqueo de [userId] (el id que presenta la lista: uuid o `anonId`). */
    fun unblock(userId: String) {
        if (_state.value.unblocking != null) return
        _state.update { it.copy(unblocking = userId, actionError = null) }
        viewModelScope.launch {
            when (val result = repository.unblock(userId)) {
                is ApiResult.Success -> _state.update { current ->
                    val remaining = (current.list as? UiState.Loaded)?.data.orEmpty().filterNot {
                        it.userId ==
                            userId
                    }
                    current.copy(list = listOrEmpty(remaining), unblocking = null)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(unblocking = null, actionError = result.error)
                }
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(actionError = null) }
    }

    private fun listOrEmpty(items: List<BlockedDto>): UiState<List<BlockedDto>> =
        if (items.isEmpty()) {
            UiState.Empty
        } else {
            UiState.Loaded(
                items.sortedByDescending {
                    it.blockedAt
                }
            )
        }
}
