package me.santiagoreina.parcheu.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.auth.AuthRepository
import me.santiagoreina.parcheu.data.auth.destination
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.Onboarding
import me.santiagoreina.parcheu.navigation.Underage

sealed interface SplashUiState {
    data object Loading : SplashUiState

    /** No se pudo consultar la sesión (sin red, error del servidor): se ofrece reintentar. */
    data class Error(val error: AppError) : SplashUiState
}

/**
 * A1 (E2.2): sin sesión → A2; con sesión → `POST /v1/session` y destino según el paso de onboarding
 * (gemelo listo → C1; sin gemelo → B1; perfil, modos o términos pendientes → B0, B2 o A4). Una cuenta
 * de menor de edad (403 UNDERAGE, E2.6) va al bloqueo por edad.
 */
class SplashViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow<SplashUiState>(SplashUiState.Loading)
    val state: StateFlow<SplashUiState> = _state.asStateFlow()

    private val _destination = Channel<AppRoute>(Channel.BUFFERED)

    /** Destino único tras decidir; la ruta deja solo esa pantalla en la pila. */
    val destination: Flow<AppRoute> = _destination.receiveAsFlow()

    init {
        resolve()
    }

    fun onRetry() {
        if (_state.value is SplashUiState.Error) resolve()
    }

    private fun resolve() {
        _state.value = SplashUiState.Loading
        viewModelScope.launch {
            if (!repository.hasSession()) {
                _destination.send(Onboarding(page = 0))
                return@launch
            }
            when (val session = repository.refreshSession()) {
                is ApiResult.Success -> _destination.send(session.value.onboarding.destination())
                is ApiResult.Failure ->
                    when (session.error) {
                        AppError.Unauthorized -> {
                            // La sesión de Firebase ya no vale para la API: se cierra y se empieza de cero.
                            repository.signOut()
                            _destination.send(Onboarding(page = 0))
                        }
                        // Cuenta de menor de edad (E2.6): la API solo deja verla o borrarla.
                        AppError.Underage -> _destination.send(Underage)
                        else -> _state.value = SplashUiState.Error(session.error)
                    }
            }
        }
    }
}
