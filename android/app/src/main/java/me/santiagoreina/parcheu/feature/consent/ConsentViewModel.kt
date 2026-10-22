package me.santiagoreina.parcheu.feature.consent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

data class ConsentUiState(val submitting: Boolean = false, val error: AppError? = null)

sealed interface ConsentEffect {
    /** B4 terminó (con o sin consentimiento): sigue B5. */
    data object Done : ConsentEffect
}

/** B4 (E2.5): 'Acepto' concede Amistad y Estudio; 'Ahora no' sigue sin conceder nada (es opcional) y revoca lo que un 'Acepto' fallido haya dejado. */
class ConsentViewModel(private val repository: ConsentRepository) : ViewModel() {
    private val _state = MutableStateFlow(ConsentUiState())
    val state: StateFlow<ConsentUiState> = _state.asStateFlow()

    private val _effects = Channel<ConsentEffect>(Channel.BUFFERED)
    val effects: Flow<ConsentEffect> = _effects.receiveAsFlow()

    fun onAccept() {
        if (_state.value.submitting) return
        _state.update { ConsentUiState(submitting = true) }
        viewModelScope.launch {
            when (val result = repository.grantOnboardingLearning()) {
                is ApiResult.Failure -> _state.update { ConsentUiState(error = result.error) }
                is ApiResult.Success -> {
                    _state.update { ConsentUiState() }
                    _effects.send(ConsentEffect.Done)
                }
            }
        }
    }

    fun onNotNow() {
        if (_state.value.submitting) return
        _state.update { ConsentUiState(submitting = true) }
        viewModelScope.launch {
            // Si un 'Acepto' anterior falló a medias, no se conserva lo que alcanzó a concederse; si la
            // revocación falla no se avanza a B5 (el usuario puede reintentar).
            when (val result = repository.revokePartialGrants()) {
                is ApiResult.Failure -> _state.update { ConsentUiState(error = result.error) }
                is ApiResult.Success -> {
                    _state.update { ConsentUiState() }
                    _effects.send(ConsentEffect.Done)
                }
            }
        }
    }
}

fun consentErrorMessage(error: AppError): String = when (error) {
    AppError.Network -> "No hay conexión. Revisa tu internet e inténtalo de nuevo."
    else -> "No pudimos guardar tu respuesta. Inténtalo de nuevo."
}
