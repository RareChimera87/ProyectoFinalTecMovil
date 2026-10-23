package me.santiagoreina.parcheu.feature.modes

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
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

/** Diálogo B3 abierto. [birthDate] y [age] son de solo lectura (la fecha la verificó B0, E2.6). */
data class ParejaDialogState(
    val birthDate: String?,
    val age: Int?,
    val confirmed: Boolean = false
) {
    /** Sin fecha verificada no se puede confirmar: el servidor respondería AGE_REQUIRED. */
    val canConfirm: Boolean get() = birthDate != null && confirmed
}

data class ModesUiState(
    val loading: Boolean = true,
    val loadError: AppError? = null,
    /** Estado de B2. Todos apagados por defecto; se llena con los modos guardados. */
    val modes: Map<Mode, Boolean> = Mode.entries.associateWith { false },
    val dialog: ParejaDialogState? = null,
    val saving: Boolean = false,
    val saveError: AppError? = null
) {
    /** Continuar exige al menos un modo activo (validación de B2). */
    val canContinue: Boolean get() = !loading && loadError == null && !saving &&
        modes.values.any { it }
}

sealed interface ModesEffect {
    /** Los modos quedaron guardados en el servidor. */
    data class Saved(val me: MeDto) : ModesEffect
}

/**
 * B2 y B3 (E2.5). Encender Pareja abre B3 y solo queda encendido al confirmar la casilla;
 * cancelar lo deja apagado. Continuar envía `PUT /v1/me/modes` con `parejaConfirmed` si la casilla
 * se marcó aquí.
 */
class ModesViewModel(
    private val repository: ModesRepository,
    private val today: () -> CivilDate = CivilDate::todayInBogota
) : ViewModel() {
    private val _state = MutableStateFlow(ModesUiState())
    val state: StateFlow<ModesUiState> = _state.asStateFlow()

    private val _effects = Channel<ModesEffect>(Channel.BUFFERED)
    val effects: Flow<ModesEffect> = _effects.receiveAsFlow()

    private var me: MeDto? = null

    /** La casilla de B3 se marcó en esta pantalla (se envía como `parejaConfirmed`). */
    private var parejaConfirmedHere = false

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = repository.me()) {
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, loadError = result.error)
                }
                is ApiResult.Success -> {
                    me = result.value
                    _state.update {
                        it.copy(
                            loading = false,
                            modes = Mode.entries.associateWith { mode ->
                                result.value.modes[mode] ==
                                    true
                            }
                        )
                    }
                }
            }
        }
    }

    fun onToggle(mode: Mode, enabled: Boolean) {
        if (_state.value.saving) return
        if (mode == Mode.PAREJA && enabled) {
            val birth = me?.takeIf { it.ageVerified }?.birthDate?.let(CivilDate::of)
            _state.update {
                it.copy(
                    dialog = ParejaDialogState(birth?.display(), birth?.ageOn(today())),
                    saveError = null
                )
            }
            return
        }
        if (mode == Mode.PAREJA) parejaConfirmedHere = false
        _state.update { it.copy(modes = it.modes + (mode to enabled), saveError = null) }
    }

    fun onDialogConfirmedChange(confirmed: Boolean) {
        _state.update { s -> s.copy(dialog = s.dialog?.copy(confirmed = confirmed)) }
    }

    /** Confirmar en B3: Pareja queda encendido. */
    fun onDialogConfirm() {
        val dialog = _state.value.dialog ?: return
        if (!dialog.canConfirm) return
        parejaConfirmedHere = true
        _state.update { it.copy(dialog = null, modes = it.modes + (Mode.PAREJA to true)) }
    }

    /** Cancelar en B3 (o atrás): Pareja sigue apagado. */
    fun onDialogCancel() {
        _state.update { it.copy(dialog = null) }
    }

    fun onContinue() {
        val current = _state.value
        if (!current.canContinue) return
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            when (val result = repository.updateModes(current.modes, parejaConfirmedHere)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(saving = false, saveError = result.error)
                }
                is ApiResult.Success -> {
                    me = result.value
                    _state.update { it.copy(saving = false) }
                    _effects.send(ModesEffect.Saved(result.value))
                }
            }
        }
    }
}

/** Mensaje de error de B2. */
fun modesErrorMessage(error: AppError): String = when (error) {
    AppError.Network -> "No hay conexión. Revisa tu internet e inténtalo de nuevo."
    AppError.AgeRequired ->
        "Para activar Pareja primero verificamos " +
            "tu fecha de nacimiento en tu perfil."
    is AppError.Validation ->
        "Revisa tus modos: activa al menos uno " +
            "y confirma Pareja si lo elegiste."
    else -> "No pudimos guardar tus modos. Inténtalo de nuevo."
}
