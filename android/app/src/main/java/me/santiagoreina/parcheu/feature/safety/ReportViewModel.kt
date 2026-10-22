package me.santiagoreina.parcheu.feature.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.HelpResource
import me.santiagoreina.parcheu.contract.ReportReason
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

/** Reporte enviado: confirmación (nueva) con las líneas de ayuda si el motivo fue autolesión. */
data class ReportSent(
    val helpResources: List<HelpResource>,
    val blocked: Boolean,
    /** Se pidió bloquear y el bloqueo falló (el reporte sí llegó). */
    val blockFailed: Boolean = false,
)

data class ReportUiState(
    val target: SafetyTarget,
    val reason: ReportReason? = null,
    val details: String = "",
    /** "Adjuntar los últimos mensajes": solo existe si el reporte sale de un chat. */
    val attachMessages: Boolean = target.chatId != null,
    val alsoBlock: Boolean = true,
    /** La persona tocó la casilla: su elección manda sobre el valor por defecto del motivo. */
    val alsoBlockTouched: Boolean = false,
    val submitting: Boolean = false,
    val error: AppError? = null,
    val sent: ReportSent? = null,
) {
    val canAttach: Boolean get() = target.chatId != null
    val canSubmit: Boolean get() = reason != null && !submitting && sent == null
}

/**
 * H2 (E8.1): motivo, detalle opcional, adjuntar los últimos mensajes y "Bloquear también". Enviar llama
 * `POST /v1/reports` (con `chatId` solo si se adjuntan los mensajes: el servidor guarda los últimos 20) y,
 * si se pidió, `POST /v1/blocks/{id}`. Ninguna de las dos depende del plan ni del cupo.
 */
class ReportViewModel(target: SafetyTarget, private val repository: SafetyRepository) : ViewModel() {
    private val _state = MutableStateFlow(ReportUiState(target))
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    fun selectReason(reason: ReportReason) {
        _state.update { it.copy(
                reason = reason,
                error = null,
                // Autolesión: no se bloquea por defecto a quien puede estar en riesgo (se corta el contacto).
                alsoBlock = if (it.alsoBlockTouched) it.alsoBlock else reason != ReportReason.SELF_HARM,
            )
        }
    }

    fun onDetailsChange(value: String) {
        _state.update { it.copy(details = value.take(MAX_REPORT_DETAILS)) }
    }

    fun setAttachMessages(value: Boolean) {
        _state.update { if (it.canAttach) it.copy(attachMessages = value) else it }
    }

    fun setAlsoBlock(value: Boolean) {
        _state.update { it.copy(alsoBlock = value, alsoBlockTouched = true) }
    }

    fun submit() {
        val current = _state.value
        val reason = current.reason ?: return
        if (!current.canSubmit) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val chatId = current.target.chatId.takeIf { current.attachMessages }
            when (val report = repository.report(current.target, reason, current.details, chatId)) {
                is ApiResult.Failure -> _state.update { it.copy(submitting = false, error = report.error) }
                is ApiResult.Success -> {
                    val blockOk = if (current.alsoBlock) {
                        repository.block(
                            current.target,
                        ) is ApiResult.Success
                    } else {
                        false
                    }
                    _state.update {
                        it.copy(
                            submitting = false,
                            sent =
                            ReportSent(
                                helpResources = report.value.helpResources.orEmpty(),
                                blocked = blockOk,
                                blockFailed = current.alsoBlock && !blockOk,
                            ),
                        )
                    }
                }
            }
        }
    }
}
