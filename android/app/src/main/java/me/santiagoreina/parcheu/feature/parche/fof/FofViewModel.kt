package me.santiagoreina.parcheu.feature.parche.fof

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
import me.santiagoreina.parcheu.navigation.Paywall

/** Origen del paywall cuando se abre desde D7 (analítica de G1). */
const val PAYWALL_SOURCE_FOF = "fof"

/** Estados de D6/D7: cargando, error, gratis (D7, solo el conteo) y desbloqueado (D6). */
sealed interface FofUiState {
    data object Loading : FofUiState

    data class Error(val error: AppError) : FofUiState

    /** D7: borroso con candado. [count] es lo único que el servidor manda a gratis. */
    data class Locked(val count: Int) : FofUiState

    /**
     * D6. [requested] son los `anonId` a los que ya se pidió presentación en esta visita; [sending] el
     * que se está pidiendo; [message] un aviso de una línea tras pedirla.
     */
    data class Unlocked(
        val content: FofContent,
        val requested: Set<String> = emptySet(),
        val sending: String? = null,
        val message: String? = null
    ) : FofUiState
}

sealed interface FofEffect {
    data class Navigate(val route: AppRoute) : FofEffect
}

/**
 * D6/D7 (E6.3). Gratis ve el bloqueo (D7) y "Ver ParcheU+" abre G1; ParcheU+ ve G0 con la capa fof y la
 * lista (D6). "Pedir presentación" usa `POST /v1/introductions/request` (E6.4) con el `anonId`.
 */
class FofViewModel(private val repository: FofRepository) : ViewModel() {
    private val _state = MutableStateFlow<FofUiState>(FofUiState.Loading)
    val state: StateFlow<FofUiState> = _state.asStateFlow()

    private val _effects = Channel<FofEffect>(Channel.BUFFERED)
    val effects: Flow<FofEffect> = _effects.receiveAsFlow()

    private var loadJob: Job? = null
    private var started = false

    /** Primera carga; repetirla no hace nada. */
    fun start() {
        if (started) return
        started = true
        load()
    }

    /** "Reintentar" del error. */
    fun retry() {
        _state.value = FofUiState.Loading
        load()
    }

    /** "Ver ParcheU+" de D7. */
    fun onUnlock() {
        viewModelScope.launch { _effects.send(FofEffect.Navigate(Paywall(PAYWALL_SOURCE_FOF))) }
    }

    /** "Pedir presentación" a un candidato (por su `anonId`). */
    fun onRequestIntroduction(anonId: String) {
        val current = _state.value as? FofUiState.Unlocked ?: return
        if (current.sending != null || anonId in current.requested) return
        _state.value = current.copy(sending = anonId, message = null)
        viewModelScope.launch {
            val result = repository.requestIntroduction(anonId)
            val latest = _state.value as? FofUiState.Unlocked ?: return@launch
            _state.value =
                when (result) {
                    is ApiResult.Success -> latest.copy(
                        sending = null,
                        requested = latest.requested + anonId,
                        message = REQUESTED_MESSAGE
                    )
                    is ApiResult.Failure -> latest.copy(
                        sending = null,
                        message = result.error.introMessage()
                    )
                }
            if (result is ApiResult.Failure && result.error == AppError.PlusRequired) {
                _effects.send(FofEffect.Navigate(Paywall(PAYWALL_SOURCE_FOF)))
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _state.value =
                    when (val result = repository.load()) {
                        is ApiResult.Failure -> FofUiState.Error(result.error)
                        is ApiResult.Success ->
                            if (result.value.plus) {
                                FofUiState.Unlocked(result.value)
                            } else {
                                FofUiState.Locked(result.value.count)
                            }
                    }
            }
    }

    companion object {
        const val REQUESTED_MESSAGE = "Listo: le pedimos a su amigo en común que los presente"
    }
}

/** Texto de una línea cuando "Pedir presentación" falla. */
fun AppError.introMessage(): String = when (this) {
    AppError.Network -> "Sin conexión. Intenta de nuevo."
    AppError.PlusRequired -> "Pedir presentaciones es de ParcheU+."
    is AppError.QuotaExceeded -> "Llegaste al límite de presentaciones por ahora."
    AppError.Blocked, AppError.NotFound -> "Esta persona ya no está disponible."
    AppError.Conflict -> "Ya pediste esta presentación."
    else -> "No pudimos pedir la presentación. Intenta más tarde."
}

/** Mensaje del error de carga de D6/D7. */
fun AppError.fofMessage(): String = when (this) {
    AppError.Network -> "Revisa tu conexión e intenta de nuevo."
    else -> "Algo salió mal. Intenta de nuevo en un momento."
}
