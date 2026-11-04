package me.santiagoreina.parcheu.feature.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.OnboardingStep
import me.santiagoreina.parcheu.contract.SessionDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.auth.AuthRepository
import me.santiagoreina.parcheu.data.auth.SignInResult
import me.santiagoreina.parcheu.data.auth.destination
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.Underage

/** Estado del login de A4 (los estados de cargando y error no están en los mockups). */
sealed interface LoginState {
    data object Idle : LoginState

    data object Loading : LoginState

    /** Falló el login o un paso posterior; la UI muestra el mensaje y "Reintentar". */
    data class Error(val error: AppError) : LoginState

    /**
     * La sesión ya existía y la versión del texto legal cambió: una hoja pide aceptar de nuevo
     * ([textVersion] es la vigente). [submitting] mientras se envía la aceptación.
     */
    data class TermsSheet(val textVersion: String, val submitting: Boolean = false) : LoginState
}

data class OnboardingUiState(val login: LoginState = LoginState.Idle)

sealed interface OnboardingEffect {
    /** Fin del onboarding de A: deja [route] como única pantalla. */
    data class Navigate(val route: AppRoute) : OnboardingEffect
}

/**
 * Login de A4 (E2.2): "Continuar con Google" implica aceptar Términos y Política (texto bajo el botón), así
 * que si la API responde `NEEDS_TERMS` se llama `POST /v1/me/terms` una vez y luego se navega según el
 * paso de onboarding. Si la persona llega a A4 con sesión y términos desactualizados, se muestra la hoja
 * de confirmación.
 */
class OnboardingViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val _effects = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects: Flow<OnboardingEffect> = _effects.receiveAsFlow()

    /**
     * `true` cuando el login de este flujo lo inició la persona en A4 (tocó "Continuar con Google" sin
     * sesión previa): solo entonces el texto bajo el botón cuenta como aceptación expresa. Una sesión
     * preexistente con términos nuevos exige siempre la hoja de reconfirmación.
     */
    private var signInStartedHere = false

    init {
        if (repository.hasSession()) {
            viewModelScope.launch {
                setLogin(LoginState.Loading)
                handleSession(repository.refreshSession(), acceptedNow = false)
            }
        }
    }

    /**
     * "Continuar con Google" y también "Reintentar". Si Firebase ya tiene sesión (falló un paso posterior),
     * no se vuelve a abrir el selector de cuentas: se repite `POST /v1/session`.
     */
    fun onContinueWithGoogle(activityContext: Context) {
        if (_state.value.login == LoginState.Loading) return
        viewModelScope.launch {
            setLogin(LoginState.Loading)
            if (repository.hasSession()) {
                handleSession(repository.refreshSession(), acceptedNow = signInStartedHere)
                return@launch
            }
            signInStartedHere = true
            when (val result = repository.signInWithGoogle(activityContext)) {
                SignInResult.Cancelled -> setLogin(LoginState.Idle)
                is SignInResult.Failure -> fail(result.error)
                is SignInResult.Success ->
                    handleSession(ApiResult.Success(result.session), acceptedNow = true)
            }
        }
    }

    /** Acepta la versión vigente desde la hoja de confirmación. */
    fun onAcceptTerms() {
        val sheet = _state.value.login as? LoginState.TermsSheet ?: return
        if (sheet.submitting) return
        viewModelScope.launch {
            setLogin(sheet.copy(submitting = true))
            acceptAndContinue(sheet.textVersion)
        }
    }

    /** Rechaza la nueva versión: sin aceptación no se puede usar la app, así que se cierra la sesión. */
    fun onDeclineTerms() {
        if (_state.value.login !is LoginState.TermsSheet) return
        viewModelScope.launch {
            repository.signOut()
            setLogin(LoginState.Idle)
        }
    }

    /** Cierra el mensaje de error sin reintentar. */
    fun onDismissError() {
        if (_state.value.login is LoginState.Error) setLogin(LoginState.Idle)
    }

    private suspend fun handleSession(result: ApiResult<SessionDto>, acceptedNow: Boolean) {
        val session =
            when (result) {
                is ApiResult.Failure -> return fail(result.error)
                is ApiResult.Success -> result.value
            }
        when {
            session.onboarding != OnboardingStep.NEEDS_TERMS -> navigate(session.onboarding)
            acceptedNow -> acceptAndContinue(session.legalTextVersion)
            else -> setLogin(LoginState.TermsSheet(session.legalTextVersion))
        }
    }

    private suspend fun acceptAndContinue(textVersion: String) {
        when (val accepted = repository.acceptTerms(textVersion)) {
            is ApiResult.Failure -> return fail(accepted.error)
            is ApiResult.Success -> Unit
        }
        when (val session = repository.refreshSession()) {
            is ApiResult.Failure -> fail(session.error)
            is ApiResult.Success ->
                if (session.value.onboarding == OnboardingStep.NEEDS_TERMS) {
                    // La API no registró la aceptación: no se entra a la app sin términos vigentes.
                    fail(AppError.TermsRequired)
                } else {
                    navigate(session.value.onboarding)
                }
        }
    }

    private suspend fun fail(error: AppError) {
        if (error == AppError.Underage) {
            // Cuenta de menor de edad (E2.6): no hay login que reintentar, solo el bloqueo por edad.
            setLogin(LoginState.Idle)
            _effects.send(OnboardingEffect.Navigate(Underage))
            return
        }
        // Un 401 que persiste tras refrescar el token: la sesión local ya no sirve.
        if (error == AppError.Unauthorized) repository.signOut()
        setLogin(LoginState.Error(error))
    }

    private suspend fun navigate(step: OnboardingStep) {
        setLogin(LoginState.Idle)
        _effects.send(OnboardingEffect.Navigate(step.destination()))
    }

    private fun setLogin(login: LoginState) {
        _state.value = _state.value.copy(login = login)
    }
}
