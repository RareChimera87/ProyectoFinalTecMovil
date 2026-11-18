package me.santiagoreina.parcheu.feature.twinready

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.modes.ModesRepository
import me.santiagoreina.parcheu.feature.profile.ProfileRepository

/**
 * B5: modos activos para las píldoras y nivel del gemelo. Si `GET /v1/me` falla la celebración se
 * muestra sin píldoras; si `GET /v1/twin` falla o aún no responde, [twinLevel] queda `null` y no hay texto.
 */
data class TwinReadyUiState(val modes: List<Mode> = emptyList(), val twinLevel: Int? = null)

class TwinReadyViewModel(
    private val repository: ModesRepository,
    private val profile: ProfileRepository
) : ViewModel() {
    private val _state = MutableStateFlow(TwinReadyUiState())
    val state: StateFlow<TwinReadyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val me = repository.me() as? ApiResult.Success ?: return@launch
            val modes = Mode.entries.filter { me.value.modes[it] == true }
            _state.update { it.copy(modes = modes) }
        }
        viewModelScope.launch {
            val twin = profile.twin() as? ApiResult.Success ?: return@launch
            _state.update { it.copy(twinLevel = twin.value.level) }
        }
    }
}
