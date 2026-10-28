package me.santiagoreina.parcheu.feature.parche.communities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.Parche
import me.santiagoreina.parcheu.navigation.Paywall

/** Origen del paywall cuando se abre desde D8 (analítica de G1). */
const val PAYWALL_SOURCE_COMMUNITIES = "communities"

/** Estados de D8: cargando, error, bloqueado (gratis, solo el conteo), vacío y con comunidades. */
sealed interface CommunitiesUiState {
    data object Loading : CommunitiesUiState

    data class Error(val error: AppError) : CommunitiesUiState

    /** Gratis: [count] es lo único que el servidor manda. */
    data class Locked(val count: Int) : CommunitiesUiState

    /** ParcheU+ sin comunidades todavía. */
    data object Empty : CommunitiesUiState

    data class Loaded(val content: CommunitiesContent) : CommunitiesUiState
}

sealed interface CommunitiesEffect {
    /** [replacing] quita D8 de la pila para que Atrás de D1 filtrado vuelva a D1. */
    data class Navigate(val route: AppRoute, val replaceCommunities: Boolean = false) :
        CommunitiesEffect
}

/**
 * D8 (E6.4). Gratis ve el bloqueo con el conteo y "Ver ParcheU+" abre G1; ParcheU+ ve sus comunidades y
 * "Ver en el grafo" abre D1 filtrado por esa comunidad.
 */
class CommunitiesViewModel(private val repository: CommunitiesRepository) : ViewModel() {
    private val _state = MutableStateFlow<CommunitiesUiState>(CommunitiesUiState.Loading)
    val state: StateFlow<CommunitiesUiState> = _state.asStateFlow()

    private val _effects = Channel<CommunitiesEffect>(Channel.BUFFERED)
    val effects: Flow<CommunitiesEffect> = _effects.receiveAsFlow()

    private var loadJob: Job? = null
    private var started = false

    /** Primera carga; repetirla no hace nada. */
    fun start() {
        if (started) return
        started = true
        load()
    }

    /** Al volver de G1 (p. ej. tras comprar ParcheU+) recarga si seguía bloqueado. */
    fun refreshIfLocked() {
        if (_state.value is CommunitiesUiState.Locked) load()
    }

    fun retry() {
        _state.value = CommunitiesUiState.Loading
        load()
    }

    fun onUnlock() {
        viewModelScope.launch {
            _effects.send(CommunitiesEffect.Navigate(Paywall(PAYWALL_SOURCE_COMMUNITIES)))
        }
    }

    /** "Ver en el grafo": D1 filtrado por [communityId]. */
    fun onViewInGraph(communityId: String) {
        viewModelScope.launch {
            _effects.send(
                CommunitiesEffect.Navigate(
                    Parche(community = communityId),
                    replaceCommunities = true
                )
            )
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _state.value =
                    when (val result = repository.load()) {
                        is ApiResult.Failure -> CommunitiesUiState.Error(result.error)
                        is ApiResult.Success ->
                            when {
                                !result.value.plus -> CommunitiesUiState.Locked(result.value.count)
                                result.value.items.isEmpty() -> CommunitiesUiState.Empty
                                else -> CommunitiesUiState.Loaded(result.value)
                            }
                    }
            }
    }
}

/** Mensaje del error de carga de D8. */
fun AppError.communitiesMessage(): String = when (this) {
    AppError.Network -> "Revisa tu conexión e intenta de nuevo."
    else -> "Algo salió mal. Intenta de nuevo en un momento."
}
