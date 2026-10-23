package me.santiagoreina.parcheu.feature.deleteaccount

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
import me.santiagoreina.parcheu.data.account.AccountRepository
import me.santiagoreina.parcheu.data.auth.AuthRepository
import me.santiagoreina.parcheu.data.auth.LOGIN_PAGE
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.ExportData
import me.santiagoreina.parcheu.navigation.Onboarding

data class DeleteAccountUiState(
    /** Casilla "Entiendo que no se puede deshacer": sin ella el botón está apagado. */
    val confirmed: Boolean = false,
    val deleting: Boolean = false,
    val error: AppError? = null,
    /** La API aceptó el borrado y la sesión local ya se cerró. */
    val deleted: Boolean = false
) {
    val canDelete: Boolean get() = confirmed && !deleting && !deleted
}

sealed interface DeleteAccountEffect {
    /** Abre [route] encima (p. ej. 'Descargar mis datos' antes de borrar). */
    data class Open(val route: AppRoute) : DeleteAccountEffect

    /** Deja [route] como única pantalla: la cuenta ya no existe. */
    data class ReplaceAll(val route: AppRoute) : DeleteAccountEffect

    /** Abre la gestión de la suscripción en Google Play (borrar la cuenta no la cancela). */
    data object OpenPlaySubscriptions : DeleteAccountEffect
}

/**
 * Confirmación de borrado (E8.3, pantalla nueva). Borrar llama `DELETE /v1/me` (borrado lógico inmediato;
 * el servidor purga después) y, si responde, cierra la sesión local ([AuthRepository.signOut] borra cachés y
 * credenciales) y muestra la confirmación; 'Listo' vuelve a A4 sin historial.
 */
class DeleteAccountViewModel(
    private val account: AccountRepository,
    private val auth: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(DeleteAccountUiState())
    val state: StateFlow<DeleteAccountUiState> = _state.asStateFlow()

    private val _effects = Channel<DeleteAccountEffect>(Channel.BUFFERED)
    val effects: Flow<DeleteAccountEffect> = _effects.receiveAsFlow()

    fun setConfirmed(value: Boolean) {
        _state.update {
            if (it.deleting ||
                it.deleted
            ) {
                it
            } else {
                it.copy(confirmed = value, error = null)
            }
        }
    }

    fun delete() {
        if (!_state.value.canDelete) return
        _state.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            when (val result = account.deleteAccount()) {
                is ApiResult.Failure -> _state.update {
                    it.copy(deleting = false, error = result.error)
                }
                is ApiResult.Success -> {
                    auth.signOut()
                    _state.update { it.copy(deleting = false, deleted = true) }
                }
            }
        }
    }

    fun onDownloadFirst() {
        viewModelScope.launch { _effects.send(DeleteAccountEffect.Open(ExportData)) }
    }

    fun onManageSubscription() {
        viewModelScope.launch { _effects.send(DeleteAccountEffect.OpenPlaySubscriptions) }
    }

    /** 'Listo' tras borrar: A4 ('Continuar con Google') como única pantalla. */
    fun onDone() {
        viewModelScope.launch {
            _effects.send(DeleteAccountEffect.ReplaceAll(Onboarding(page = LOGIN_PAGE)))
        }
    }
}
