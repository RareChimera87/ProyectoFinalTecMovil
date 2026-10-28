package me.santiagoreina.parcheu.feature.notifications.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.ConnectRequest
import me.santiagoreina.parcheu.contract.ConnectRequestsDto
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.Paywall
import me.santiagoreina.parcheu.navigation.Results

/** Origen del paywall cuando se abre desde el ítem bloqueado de 'quieren conectar contigo'. */
const val PAYWALL_SOURCE_CONNECT_REQUESTS = "connect_requests"

/**
 * Estado de la sección de C2. [Hidden] cubre cargando, sin solicitudes y error: la sección es un extra de
 * la bandeja y nunca debe estorbarla, así que un fallo no se muestra (se reintenta al volver a C2).
 */
sealed interface ConnectRequestsUiState {
    data object Hidden : ConnectRequestsUiState

    /** Gratis: solo el conteo (ítem bloqueado). */
    data class Locked(val count: Int) : ConnectRequestsUiState

    /** ParcheU+: la lista de quienes decidieron conectar y aún no respondes. */
    data class Plus(val items: List<ConnectRequest>) : ConnectRequestsUiState
}

internal fun ConnectRequestsDto.toUiState(): ConnectRequestsUiState {
    val list = items
    return when {
        // El servidor omite `items` para gratis: solo el conteo, que si es 0 no deja nada que mostrar.
        list == null -> if (count >
            0
        ) {
            ConnectRequestsUiState.Locked(count)
        } else {
            ConnectRequestsUiState.Hidden
        }
        list.isEmpty() -> ConnectRequestsUiState.Hidden
        else -> ConnectRequestsUiState.Plus(list)
    }
}

sealed interface ConnectRequestsEffect {
    data class Navigate(val route: AppRoute) : ConnectRequestsEffect
}

/**
 * Sección 'Quieren conectar contigo' de C2 (E6.6). Gratis ve un ítem bloqueado con el conteo que abre el
 * paywall (G1); Plus ve la lista y cada fila abre el resultado de esa simulación (F7/F8), donde responde.
 * Responder la saca de la lista en la siguiente carga ([load] al volver a C2).
 */
class ConnectRequestsViewModel(private val repository: ConnectRequestsRepository) : ViewModel() {
    private val _state = MutableStateFlow<ConnectRequestsUiState>(ConnectRequestsUiState.Hidden)
    val state: StateFlow<ConnectRequestsUiState> = _state.asStateFlow()

    private val _effects = Channel<ConnectRequestsEffect>(Channel.BUFFERED)
    val effects: Flow<ConnectRequestsEffect> = _effects.receiveAsFlow()

    /** Carga o refresca (la ruta lo llama al entrar y al volver a C2). Un error deja lo que hubiera en pantalla. */
    fun load() {
        viewModelScope.launch {
            when (val result = repository.requests()) {
                is ApiResult.Success -> _state.value = result.value.toUiState()
                is ApiResult.Failure -> Unit
            }
        }
    }

    fun onOpenLocked() = navigate(Paywall(PAYWALL_SOURCE_CONNECT_REQUESTS))

    fun onOpen(request: ConnectRequest) = navigate(Results(request.simulationId))

    private fun navigate(route: AppRoute) {
        viewModelScope.launch { _effects.send(ConnectRequestsEffect.Navigate(route)) }
    }
}
