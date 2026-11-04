package me.santiagoreina.parcheu.feature.privacysettings

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
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.PrivacyDto
import me.santiagoreina.parcheu.contract.Visibility
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.auth.AuthRepository
import me.santiagoreina.parcheu.data.auth.LOGIN_PAGE
import me.santiagoreina.parcheu.feature.modes.CivilDate
import me.santiagoreina.parcheu.feature.modes.ModesRepository
import me.santiagoreina.parcheu.feature.modes.ParejaDialogState
import me.santiagoreina.parcheu.feature.modes.ageOn
import me.santiagoreina.parcheu.feature.modes.display
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.Onboarding

data class PrivacySettingsUiState(
    val loading: Boolean = true,
    val loadError: AppError? = null,
    val visibility: Visibility = Visibility.ANONYMOUS,
    val modes: Map<Mode, Boolean> = Mode.entries.associateWith { false },
    /** Si el gemelo aprende de los chats de cada modo. Pareja arranca apagado. */
    val learning: Map<Mode, Boolean> = Mode.entries.associateWith { false },
    /** B3 abierto: se pidió activar Pareja desde H3. */
    val dialog: ParejaDialogState? = null,
    /** Hay un cambio en vuelo: los controles se apagan para no encadenar escrituras. */
    val busy: Boolean = false,
    /** La última acción falló (los controles vuelven a su valor guardado). */
    val error: SettingsError? = null,
    val signingOut: Boolean = false
) {
    val interactive: Boolean get() = !loading && loadError == null && !busy && !signingOut
}

/** Fallos de una acción de H3 que tienen texto propio. */
sealed interface SettingsError {
    /** Apagar el último modo activo: siempre debe quedar uno. */
    data object LastMode : SettingsError

    data class Api(val error: AppError) : SettingsError
}

sealed interface PrivacySettingsEffect {
    /** Deja [route] como única pantalla de la pila (cierre de sesión: A4). */
    data class ReplaceAll(val route: AppRoute) : PrivacySettingsEffect
}

/**
 * H3 'Ajustes de privacidad' (E8.2). Los modos se guardan con `PUT /v1/me/modes`; encender Pareja abre B3
 * (el mismo diálogo de E2.5: sin fecha de nacimiento verificada no se puede confirmar). Visibilidad y
 * aprendizaje por modo van por `PATCH /v1/me/privacy`. 'Cerrar sesión' delega en [AuthRepository.signOut]
 * (token FCM, datos locales, Firestore y credenciales) y deja A4 como única pantalla.
 */
class PrivacySettingsViewModel(
    private val privacy: PrivacySettingsRepository,
    private val modes: ModesRepository,
    private val auth: AuthRepository,
    private val today: () -> CivilDate = CivilDate::todayInBogota
) : ViewModel() {
    private val _state = MutableStateFlow(PrivacySettingsUiState())
    val state: StateFlow<PrivacySettingsUiState> = _state.asStateFlow()

    private val _effects = Channel<PrivacySettingsEffect>(Channel.BUFFERED)
    val effects: Flow<PrivacySettingsEffect> = _effects.receiveAsFlow()

    private var me: MeDto? = null

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null, error = null) }
        viewModelScope.launch {
            val meResult = modes.me()
            val privacyResult = privacy.privacy()
            when {
                meResult is ApiResult.Failure ->
                    _state.update { it.copy(loading = false, loadError = meResult.error) }
                privacyResult is ApiResult.Failure ->
                    _state.update { it.copy(loading = false, loadError = privacyResult.error) }
                meResult is ApiResult.Success && privacyResult is ApiResult.Success -> {
                    me = meResult.value
                    _state.update { it.copy(loading = false).applying(privacyResult.value) }
                }
            }
        }
    }

    fun onModeToggle(mode: Mode, enabled: Boolean) {
        val current = _state.value
        if (!current.interactive) return
        if (mode == Mode.PAREJA && enabled) {
            val birth = me?.takeIf { it.ageVerified }?.birthDate?.let(CivilDate::of)
            _state.update {
                it.copy(
                    dialog = ParejaDialogState(birth?.display(), birth?.ageOn(today())),
                    error = null
                )
            }
            return
        }
        val target = current.modes + (mode to enabled)
        if (target.values.none { it }) {
            _state.update { it.copy(error = SettingsError.LastMode) }
            return
        }
        saveModes(target, parejaConfirmed = false)
    }

    fun onDialogConfirmedChange(confirmed: Boolean) {
        _state.update { s -> s.copy(dialog = s.dialog?.copy(confirmed = confirmed)) }
    }

    /** Confirmar en B3: guarda Pareja encendido con la confirmación explícita. */
    fun onDialogConfirm() {
        val dialog = _state.value.dialog ?: return
        if (!dialog.canConfirm || !_state.value.interactive) return
        _state.update { it.copy(dialog = null) }
        saveModes(_state.value.modes + (Mode.PAREJA to true), parejaConfirmed = true)
    }

    /** Cancelar en B3 (o atrás): Pareja sigue apagado. */
    fun onDialogCancel() {
        _state.update { it.copy(dialog = null) }
    }

    fun onVisibility(visibility: Visibility) {
        val current = _state.value
        if (!current.interactive || current.visibility == visibility) return
        patch(visibility = visibility, learning = null)
    }

    fun onLearningToggle(mode: Mode, enabled: Boolean) {
        val current = _state.value
        if (!current.interactive) return
        // Aprender de Pareja exige Pareja activo; el servidor también lo valida (403 AGE_REQUIRED).
        if (enabled && mode == Mode.PAREJA && current.modes[Mode.PAREJA] != true) return
        patch(visibility = null, learning = mapOf(mode to enabled))
    }

    /** 'Cerrar sesión': borra todo lo local y deja A4 como única pantalla. */
    fun onSignOut() {
        if (_state.value.signingOut) return
        _state.update { it.copy(signingOut = true, error = null) }
        viewModelScope.launch {
            auth.signOut()
            _effects.send(PrivacySettingsEffect.ReplaceAll(Onboarding(page = LOGIN_PAGE)))
        }
    }

    private fun saveModes(target: Map<Mode, Boolean>, parejaConfirmed: Boolean) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = modes.updateModes(target, parejaConfirmed)) {
                is ApiResult.Failure -> fail(result.error)
                is ApiResult.Success -> {
                    me = result.value
                    // Apagar Pareja revoca su aprendizaje en el servidor: se vuelve a leer para reflejarlo.
                    when (val fresh = privacy.privacy()) {
                        is ApiResult.Success ->
                            _state.update { it.copy(busy = false).applying(fresh.value) }
                        is ApiResult.Failure -> _state.update {
                            it.copy(
                                busy = false,
                                modes = Mode.entries.associateWith { m ->
                                    result.value.modes[m] ==
                                        true
                                },
                                error = SettingsError.Api(fresh.error)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun patch(visibility: Visibility?, learning: Map<Mode, Boolean>?) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = privacy.update(visibility, learning)) {
                is ApiResult.Failure -> fail(result.error)
                is ApiResult.Success -> _state.update {
                    it.copy(busy = false).applying(result.value)
                }
            }
        }
    }

    private fun fail(error: AppError) {
        _state.update { it.copy(busy = false, error = SettingsError.Api(error)) }
    }
}

private fun PrivacySettingsUiState.applying(dto: PrivacyDto) = copy(
    visibility = dto.visibility,
    modes = Mode.entries.associateWith { dto.modes[it] == true },
    learning = Mode.entries.associateWith { dto.learning[it] == true }
)

/** Texto del error de H3. */
fun settingsErrorMessage(error: SettingsError): String = when (error) {
    SettingsError.LastMode -> "Deja al menos un modo activo."
    is SettingsError.Api -> when (error.error) {
        AppError.Network -> "No hay conexión. Revisa tu internet e inténtalo de nuevo."
        AppError.AgeRequired ->
            "Para activar Pareja primero verificamos tu fecha de nacimiento en tu perfil."
        else -> "No pudimos guardar el cambio. Inténtalo de nuevo."
    }
}

/** Texto cuando la pantalla no carga. */
fun settingsLoadErrorMessage(error: AppError): String = when (error) {
    AppError.Network -> "No hay conexión. Revisa tu internet e inténtalo de nuevo."
    else -> "No pudimos cargar tus ajustes de privacidad."
}
