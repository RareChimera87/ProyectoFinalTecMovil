package me.santiagoreina.parcheu.feature.exportdata

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

/** Qué hacer con el JSON una vez descargado. */
enum class ExportTarget { SAVE, SHARE }

/** Resultado visible del último intento. */
enum class ExportOutcome { SAVED, SHARED, CANCELLED, WRITE_FAILED }

data class ExportDataUiState(
    /** Pidiendo el JSON a la API; [target] dice para qué. */
    val loading: ExportTarget? = null,
    val error: AppError? = null,
    val outcome: ExportOutcome? = null
) {
    val busy: Boolean get() = loading != null
}

sealed interface ExportDataEffect {
    /** Lanza `ACTION_CREATE_DOCUMENT` (`application/json`) con [fileName] como título sugerido. */
    data class CreateDocument(val fileName: String) : ExportDataEffect

    /** Abre la hoja del sistema para compartir el archivo [uri] (`content://` del FileProvider). */
    data class Share(val uri: String, val fileName: String) : ExportDataEffect
}

/**
 * 'Descargar mis datos' (E8.3, Ley 1581). Pide `GET /v1/me/export` y lo guarda donde la persona elija
 * (`ACTION_CREATE_DOCUMENT` con `parcheu-datos-<fecha>.json`) o lo comparte con la hoja del sistema.
 * El JSON solo vive en memoria mientras se elige el destino (si el proceso muere, se vuelve a pedir).
 */
class ExportDataViewModel(
    private val account: AccountRepository,
    private val files: ExportFileStore,
    private val today: () -> String = ::todayInBogota
) : ViewModel() {
    private val _state = MutableStateFlow(ExportDataUiState())
    val state: StateFlow<ExportDataUiState> = _state.asStateFlow()

    private val _effects = Channel<ExportDataEffect>(Channel.BUFFERED)
    val effects: Flow<ExportDataEffect> = _effects.receiveAsFlow()

    /** JSON descargado a la espera de que la persona elija dónde guardarlo. */
    private var pending: String? = null

    fun onDownload() = fetch(ExportTarget.SAVE)

    fun onShare() = fetch(ExportTarget.SHARE)

    private fun fetch(target: ExportTarget) {
        if (_state.value.busy) return
        _state.update { it.copy(loading = target, error = null, outcome = null) }
        viewModelScope.launch {
            when (val result = account.exportJson()) {
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = null, error = result.error)
                }
                is ApiResult.Success -> {
                    val fileName = exportFileName(today())
                    when (target) {
                        ExportTarget.SAVE -> {
                            pending = result.value
                            _state.update { it.copy(loading = null) }
                            _effects.send(ExportDataEffect.CreateDocument(fileName))
                        }
                        ExportTarget.SHARE -> {
                            val uri = files.shareable(fileName, result.value)
                            if (uri == null) {
                                _state.update {
                                    it.copy(loading = null, outcome = ExportOutcome.WRITE_FAILED)
                                }
                            } else {
                                _state.update {
                                    it.copy(loading = null, outcome = ExportOutcome.SHARED)
                                }
                                _effects.send(ExportDataEffect.Share(uri, fileName))
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Resultado de `ACTION_CREATE_DOCUMENT`: [uri] nulo si la persona canceló. Si el sistema mató el
     * proceso mientras el selector estaba abierto, el ViewModel es nuevo y ya no tiene el JSON, pero el
     * documento ya existe (vacío): se vuelve a pedir la exportación y se escribe en ese [uri].
     */
    fun onDocumentCreated(uri: String?) {
        val json = pending
        pending = null
        if (uri == null) {
            _state.update { it.copy(outcome = ExportOutcome.CANCELLED) }
            return
        }
        if (json == null) _state.update { it.copy(loading = ExportTarget.SAVE, error = null) }
        viewModelScope.launch {
            val content = json ?: when (val result = account.exportJson()) {
                is ApiResult.Failure -> {
                    _state.update { it.copy(loading = null, error = result.error) }
                    return@launch
                }
                is ApiResult.Success -> result.value
            }
            val ok = files.writeTo(uri, content)
            _state.update {
                it.copy(
                    loading = null,
                    outcome = if (ok) ExportOutcome.SAVED else ExportOutcome.WRITE_FAILED
                )
            }
        }
    }

    override fun onCleared() {
        pending = null
        files.clear()
    }
}
