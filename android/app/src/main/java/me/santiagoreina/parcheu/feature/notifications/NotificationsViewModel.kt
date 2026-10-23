package me.santiagoreina.parcheu.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.NotificationDto
import me.santiagoreina.parcheu.contract.NotificationsDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.Notifications
import me.santiagoreina.parcheu.navigation.Paywall
import me.santiagoreina.parcheu.push.DeepLinks

/** Origen del paywall cuando se abre desde un aviso bloqueado de C2 (analítica de G1). */
const val PAYWALL_SOURCE_NOTIFICATIONS = "notifications"

sealed interface NotificationsUiState {
    data object Loading : NotificationsUiState

    /** C2. [actionError] es el aviso de un 'Marcar leídas' que falló. */
    data class Loaded(val inbox: NotificationsDto, val actionError: AppError? = null) :
        NotificationsUiState {
        val all: List<NotificationDto> get() = inbox.today + inbox.week + inbox.older

        /** Estado vacío de C2: ningún aviso. */
        val isEmpty: Boolean get() = all.isEmpty()

        val unread: Int get() = all.count { !it.read }
    }

    data class Error(val error: AppError) : NotificationsUiState
}

sealed interface NotificationsEffect {
    data class Navigate(val route: AppRoute) : NotificationsEffect
}

/**
 * C2 (E5.4). Carga la bandeja al entrar. Tocar un aviso lo marca como leído y abre su ruta con la misma
 * tabla del push ([DeepLinks], E5.3); un aviso bloqueado abre el paywall (G1). 'Marcar leídas' marca
 * todas de forma optimista y, si el backend falla, vuelve a no leídas solo las que lo eran y nadie abrió
 * mientras tanto (sobre la bandeja actual, sin pisar un refresco).
 */
class NotificationsViewModel(private val repository: NotificationsRepository) : ViewModel() {
    private val _state = MutableStateFlow<NotificationsUiState>(NotificationsUiState.Loading)
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    private val _effects = Channel<NotificationsEffect>(Channel.BUFFERED)
    val effects: Flow<NotificationsEffect> = _effects.receiveAsFlow()

    /** Ids marcados leídos al abrirlos; un 'Marcar leídas' fallido no los revierte. */
    private val openedIds = mutableSetOf<String>()

    init {
        load()
    }

    /** Carga o refresca. Con datos en pantalla refresca en silencio; un error solo reemplaza la carga inicial. */
    fun load() {
        val current = _state.value
        if (current !is NotificationsUiState.Loaded) _state.value = NotificationsUiState.Loading
        viewModelScope.launch {
            when (val result = repository.inbox()) {
                is ApiResult.Success -> _state.value = NotificationsUiState.Loaded(result.value)
                is ApiResult.Failure ->
                    if (current !is NotificationsUiState.Loaded) {
                        _state.value = NotificationsUiState.Error(result.error)
                    }
            }
        }
    }

    /** Abre el aviso: lo marca leído (best effort) y navega a su destino; si el destino es C2, se queda. */
    fun onOpen(item: NotificationDto) {
        if (!item.read) {
            openedIds += item.id
            update {
                it.copy(
                    inbox = it.inbox.mapItems { n ->
                        if (n.id ==
                            item.id
                        ) {
                            n.copy(read = true)
                        } else {
                            n
                        }
                    }
                )
            }
            // Si falla, la próxima carga lo vuelve a mostrar como no leído: no bloquea la navegación.
            viewModelScope.launch { repository.markRead(listOf(item.id)) }
        }
        val destination =
            if (item.locked) {
                Paywall(PAYWALL_SOURCE_NOTIFICATIONS)
            } else {
                DeepLinks.resolve(item.kind, item.route)
            }
        if (destination == Notifications) return
        viewModelScope.launch { _effects.send(NotificationsEffect.Navigate(destination)) }
    }

    fun onMarkAllRead() {
        val loaded = _state.value as? NotificationsUiState.Loaded ?: return
        if (loaded.unread == 0) return
        val wasUnread = loaded.all.filterNot { it.read }.map { it.id }.toSet()
        _state.value =
            loaded.copy(inbox = loaded.inbox.mapItems { it.copy(read = true) }, actionError = null)
        viewModelScope.launch {
            val result = repository.markRead(null)
            if (result is ApiResult.Failure) {
                update {
                    it.copy(
                        inbox = it.inbox.mapItems { n ->
                            if (n.id in wasUnread && n.id !in openedIds) n.copy(read = false) else n
                        },
                        actionError = result.error
                    )
                }
            }
        }
    }

    fun onDismissError() = update { it.copy(actionError = null) }

    private fun update(block: (NotificationsUiState.Loaded) -> NotificationsUiState.Loaded) {
        val current = _state.value as? NotificationsUiState.Loaded ?: return
        _state.value = block(current)
    }
}

private fun NotificationsDto.mapItems(block: (NotificationDto) -> NotificationDto) =
    NotificationsDto(
        today = today.map(block),
        week = week.map(block),
        older = older.map(block)
    )
