package me.santiagoreina.parcheu.feature.invites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.InviteDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.PendingInvite
import me.santiagoreina.parcheu.contract.RedeemStatus
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.designsystem.components.compound.QrMatrix

/** Pestañas de C5. */
enum class InvitesTab { MY_CODE, SCAN }

/** Aviso bajo el campo de código: resultado de un canje, de leer un QR o un error. */
data class InviteMessage(val text: String, val isError: Boolean)

data class InvitesUiState(
    val tab: InvitesTab = InvitesTab.MY_CODE,
    val loading: Boolean = true,
    val invite: InviteDto? = null,
    val qr: QrMatrix? = null,
    /** Nombre bajo el QR (el de la cuenta de Google); "Tu código" si no se conoce. */
    val name: String? = null,
    val loadError: AppError? = null,
    /** Código de otra persona: escrito a mano, leído de un QR o precargado desde el App Link. */
    val codeInput: String = "",
    val mode: Mode = Mode.AMISTAD,
    val submitting: Boolean = false,
    val message: InviteMessage? = null,
    /** `id` del canje pendiente (persona y modo) que se está aceptando. */
    val accepting: String? = null
) {
    val pending: List<PendingInvite> get() = invite?.pending.orEmpty()
    val canSubmit: Boolean get() = !submitting && codeInput.isNotBlank()
}

/**
 * C5 · Agregar amigos (E6.1). 'Mi código' muestra el QR (ZXing) y el código `XXXX-0000` para
 * compartir,
 * los canjes por aceptar y el campo para ingresar un código; 'Escanear' abre Google Code Scanner
 * (sin
 * permiso de cámara). Un QR con el App Link de ParcheU, o el App Link que abrió la app
 * ([initialCode]),
 * precarga el código; cualquier otro QR muestra 'Este código no es de ParcheU'.
 */
class InvitesViewModel(
    private val repository: InvitesRepository,
    initialCode: String? = null,
    private val displayName: () -> String? = { null },
    private val host: String = InviteLinks.defaultHost
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            InvitesUiState(codeInput = initialCode?.let(InviteLinks::normalize).orEmpty())
        )
    val state: StateFlow<InvitesUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = repository.myInvite()) {
                is ApiResult.Success -> {
                    val invite = result.value
                    _state.update {
                        it.copy(
                            loading = false,
                            invite = invite,
                            qr = runCatching { QrEncoder.encode(invite.link) }.getOrNull(),
                            name = displayName()?.trim()?.takeIf { name -> name.isNotEmpty() }
                        )
                    }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, loadError = result.error)
                }
            }
        }
    }

    fun onTab(tab: InvitesTab) = _state.update { it.copy(tab = tab) }

    fun onCodeChange(value: String) = _state.update {
        it.copy(codeInput = value.uppercase().take(MAX_INPUT), message = null)
    }

    fun onMode(mode: Mode) = _state.update { it.copy(mode = mode) }

    /** Abre el lector y precarga el código si el QR es de ParcheU. */
    fun onScan(scanner: CodeScanner) {
        viewModelScope.launch {
            when (val result = scanner.scan()) {
                is ScanResult.Scanned -> {
                    val code = InviteLinks.codeFromScan(result.raw, host)
                    _state.update {
                        if (code == null) {
                            it.copy(message = InviteMessage(NOT_PARCHEU, isError = true))
                        } else {
                            it.copy(
                                codeInput = code,
                                message = InviteMessage(
                                    "Código leído: $code. Elige el modo y toca Agregar.",
                                    isError = false
                                )
                            )
                        }
                    }
                }
                ScanResult.Cancelled -> Unit
                ScanResult.Unavailable -> _state.update {
                    it.copy(
                        message = InviteMessage(
                            "No pudimos abrir el lector. Escribe el código a mano.",
                            isError = true
                        )
                    )
                }
            }
        }
    }

    /** 'Agregar': canjea el código en el modo elegido. */
    fun onAdd() {
        val current = _state.value
        if (current.submitting) return
        val code = InviteLinks.normalize(current.codeInput)
        val error = when {
            !InviteLinks.isValidCode(code) -> "Escribe un código como ABCD-1234."
            code == current.invite?.code -> "Ese es tu propio código: compártelo con tus amigos."
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(message = InviteMessage(error, isError = true)) }
            return
        }
        _state.update { it.copy(submitting = true, codeInput = code, message = null) }
        viewModelScope.launch {
            val result = repository.redeem(code, current.mode)
            _state.update { state ->
                when (result) {
                    is ApiResult.Success -> {
                        val other = result.value.other.label
                        val mode = current.mode.label()
                        val text = when (result.value.status) {
                            RedeemStatus.PENDING ->
                                "Listo: le avisamos a $other. Cuando acepte, quedan " +
                                    "conectados en $mode."
                            RedeemStatus.CONNECTED -> "Ya estás conectado con $other en $mode."
                        }
                        state.copy(
                            submitting = false,
                            codeInput = "",
                            message = InviteMessage(text, isError = false)
                        )
                    }
                    is ApiResult.Failure -> state.copy(
                        submitting = false,
                        message = InviteMessage(redeemErrorMessage(result.error), isError = true)
                    )
                }
            }
        }
    }

    /**
     * El dueño del código acepta un canje pendiente: quedan CONNECTED solo en ese modo. Si la misma
     * persona canjeó en otro modo (p. ej. Pareja), ese canje sigue en la lista hasta que lo acepte.
     */
    fun onAccept(pending: PendingInvite) {
        if (_state.value.accepting != null) return
        _state.update { it.copy(accepting = pending.id, message = null) }
        viewModelScope.launch {
            when (val result = repository.accept(pending.id, pending.mode)) {
                is ApiResult.Success -> _state.update { state ->
                    state.copy(
                        accepting = null,
                        invite = state.invite?.copy(pending = state.pending.without(pending)),
                        message = InviteMessage(
                            "Ahora estás conectado con ${result.value.other.label} en " +
                                "${pending.mode.label()}.",
                            isError = false
                        )
                    )
                }
                is ApiResult.Failure -> _state.update { state ->
                    state.copy(
                        accepting = null,
                        // Ya no existe (aceptado en otro dispositivo, bloqueo o cuenta borrada): se
                        // quita.
                        invite = if (result.error == AppError.NotFound) {
                            state.invite?.copy(pending = state.pending.without(pending))
                        } else {
                            state.invite
                        },
                        message = InviteMessage(acceptErrorMessage(result.error), isError = true)
                    )
                }
            }
        }
    }

    companion object {
        const val NOT_PARCHEU = "Este código no es de ParcheU"
        private const val MAX_INPUT = 9
    }
}

internal fun Mode.label(): String = when (this) {
    Mode.AMISTAD -> "Amistad"
    Mode.PAREJA -> "Pareja"
    Mode.ESTUDIO -> "Estudio"
}

/** Quita solo ese canje (persona y modo): los de otros modos de la misma persona siguen. */
private fun List<PendingInvite>.without(pending: PendingInvite) =
    filterNot { it.id == pending.id && it.mode == pending.mode }

internal fun redeemErrorMessage(error: AppError): String = when (error) {
    AppError.NotFound -> "Ese código no existe. Revisa que esté bien escrito."
    AppError.Blocked -> "No puedes agregar a esta persona."
    // 403 FORBIDDEN del canje: límite diario de códigos por persona.
    AppError.Forbidden -> "Llegaste al límite de códigos por hoy. Inténtalo de nuevo mañana."
    is AppError.Validation -> error.message
    AppError.Network -> "Sin conexión. Inténtalo de nuevo."
    else -> "No pudimos agregar a esa persona. Inténtalo de nuevo."
}

internal fun acceptErrorMessage(error: AppError): String = when (error) {
    AppError.NotFound -> "Esa invitación ya no está disponible."
    AppError.Network -> "Sin conexión. Inténtalo de nuevo."
    else -> "No pudimos aceptar la invitación. Inténtalo de nuevo."
}
