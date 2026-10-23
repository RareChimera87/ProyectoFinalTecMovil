package me.santiagoreina.parcheu.feature.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.ChatSummary
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.ui.UiState
import me.santiagoreina.parcheu.feature.chat.ChatRepository

/**
 * Lista de chats (pantalla nueva de E5.2). Carga `GET /v1/chats` al abrirse y cada vez que la pantalla
 * vuelve a primer plano ([refresh]), así el contador de no leídos se actualiza al volver de F9.
 */
class ChatListViewModel(private val repository: ChatRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<ChatSummary>>>(UiState.Loading)
    val state: StateFlow<UiState<List<ChatSummary>>> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Recarga. Si ya hay una lista la deja visible mientras llega la nueva; un fallo no la borra. */
    fun refresh() {
        viewModelScope.launch {
            when (val result = repository.chats()) {
                is ApiResult.Success ->
                    _state.value =
                        if (result.value.isEmpty()) {
                            UiState.Empty
                        } else {
                            UiState.Loaded(
                                sorted(result.value)
                            )
                        }
                is ApiResult.Failure ->
                    if (_state.value !is UiState.Loaded) _state.value = UiState.Error(result.error)
            }
        }
    }

    fun retry() {
        _state.value = UiState.Loading
        refresh()
    }

    /** Los chats con actividad más reciente primero; sin mensajes, al final. */
    private fun sorted(chats: List<ChatSummary>): List<ChatSummary> =
        chats.sortedByDescending { it.lastMessage?.createdAt }
}
