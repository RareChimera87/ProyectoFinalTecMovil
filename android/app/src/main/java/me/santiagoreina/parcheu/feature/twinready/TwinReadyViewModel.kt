package me.santiagoreina.parcheu.feature.twinready

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.modes.ModesRepository

/** B5: modos activos para las píldoras. Si `GET /v1/me` falla la celebración se muestra sin píldoras. */
data class TwinReadyUiState(val modes: List<Mode> = emptyList())

class TwinReadyViewModel(private val repository: ModesRepository) : ViewModel() {
    private val _state = MutableStateFlow(TwinReadyUiState())
    val state: StateFlow<TwinReadyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val me = repository.me() as? ApiResult.Success ?: return@launch
            _state.value = TwinReadyUiState(Mode.entries.filter { me.value.modes[it] == true })
        }
    }
}
