package me.santiagoreina.parcheu.feature.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.Calendar
import java.util.TimeZone
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.DecisionStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.contract.ReportDto
import me.santiagoreina.parcheu.contract.TranscriptDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.results.ResultsRepository
import me.santiagoreina.parcheu.feature.plus.PlusErrorRouter
import me.santiagoreina.parcheu.navigation.AppRoute

/** En qué va la decisión de conectar, vista desde quien mira. */
enum class DecisionState {
    /** Aún no elige: se muestra el DecisionFooter. */
    PENDING,

    /** Eligió Conectar y la otra persona no ha decidido ('esperando a la otra persona'). */
    WAITING,

    /** Eligió Ahora no, o la otra persona no quiso: no hubo conexión. */
    DECLINED,

    /** Las dos conectaron: hay chat DIRECT y nombre. */
    CONNECTED
}

/** Lo que está en curso (deshabilita los botones). */
enum class Busy { UNLOCK, DECIDE }

/** Avisos dentro de la pantalla. */
sealed interface ResultsNotice {
    data class Failed(val error: AppError) : ResultsNotice
}

sealed interface TranscriptState {
    data object Loading : TranscriptState

    data class Loaded(val transcript: TranscriptDto) : TranscriptState

    data class Error(val error: AppError) : TranscriptState
}

/**
 * Datos de F7/F8. [usedMonth] y [resetMonth] ('octubre', 'noviembre') salen de
 * `QuotaDto.fullReport.resetsAt` en hora de Bogotá; son nulos si no se pudo consultar el cupo.
 */
data class ResultsContent(
    val otherLabel: String,
    val mode: Mode,
    val isDemo: Boolean,
    val plus: Boolean,
    val report: ReportDto,
    val decision: DecisionState,
    val chatId: String? = null,
    val usedMonth: String? = null,
    val resetMonth: String? = null,
    val busy: Busy? = null,
    val notice: ResultsNotice? = null,
    /** No nulo mientras se muestra la conversación de los gemelos. */
    val transcript: TranscriptState? = null
) {
    /** F8 (completo) o F7 (bloqueado). */
    val full: Boolean get() = !report.locked

    /** Nombre si ya conectaron; si no, la etiqueta anónima ('Alguien de la UIS'). */
    val displayName: String get() = report.otherName ?: otherLabel
}

sealed interface ResultsUiState {
    data object Loading : ResultsUiState

    data class Loaded(val content: ResultsContent) : ResultsUiState

    data class Error(val error: AppError) : ResultsUiState
}

sealed interface ResultsEffect {
    data class OpenChat(val chatId: String) : ResultsEffect

    data object OpenPaywall : ResultsEffect

    /** Abre la hoja G2 del cupo agotado (E7.3), por ejemplo el informe del mes ya usado. */
    data class OpenLimit(val route: AppRoute) : ResultsEffect
}

/**
 * F7, F8, I3 y 'esperando a la otra persona' (E4.6). Lee el resumen de la simulación (modo, con
 * quién, insignia demo), el informe redactado por el servidor y el cupo (Plus y mes del informe
 * gratis).
 */
class ResultsViewModel(
    private val simulationId: String,
    private val repository: ResultsRepository
) : ViewModel() {
    private val _state = MutableStateFlow<ResultsUiState>(ResultsUiState.Loading)
    val state: StateFlow<ResultsUiState> = _state.asStateFlow()

    private val _effects = Channel<ResultsEffect>(Channel.BUFFERED)
    val effects: Flow<ResultsEffect> = _effects.receiveAsFlow()

    init {
        load()
    }

    fun onRetry() = load()

    private fun load() {
        _state.value = ResultsUiState.Loading
        viewModelScope.launch {
            val simulation = async { repository.simulation(simulationId) }
            val report = async { repository.report(simulationId) }
            val quota = async { repository.quota() }
            val summary = when (val result = simulation.await()) {
                is ApiResult.Success -> result.value.summary
                is ApiResult.Failure -> {
                    _state.value = ResultsUiState.Error(result.error)
                    return@launch
                }
            }
            val dto = when (val result = report.await()) {
                is ApiResult.Success -> result.value
                is ApiResult.Failure -> {
                    _state.value = ResultsUiState.Error(result.error)
                    return@launch
                }
            }
            val quotaDto = (quota.await() as? ApiResult.Success)?.value
            val months = quotaDto?.let { monthsOf(it.fullReport.resetsAt) }
            _state.value = ResultsUiState.Loaded(
                ResultsContent(
                    otherLabel = summary.other.label,
                    mode = summary.mode,
                    isDemo = summary.other.isDemo,
                    plus = quotaDto?.plus ?: false,
                    report = dto,
                    decision = decisionOf(dto),
                    chatId = dto.chatId,
                    usedMonth = months?.first,
                    resetMonth = months?.second
                )
            )
        }
    }

    /** 'Ver informe completo' de F7 cuando queda el informe del mes. */
    fun onUnlock() {
        val content = loaded() ?: return
        if (content.busy != null || content.full) return
        update { it.copy(busy = Busy.UNLOCK, notice = null) }
        viewModelScope.launch {
            when (val result = repository.unlock(simulationId)) {
                is ApiResult.Success -> update {
                    it.copy(report = result.value, decision = decisionOf(result.value), busy = null)
                }
                is ApiResult.Failure -> {
                    val error = result.error
                    if (error is AppError.QuotaExceeded && error.kind == QuotaKind.FULL_REPORT) {
                        // El informe del mes ya se usó (otro dispositivo, p. ej.): se abre la G2 de E7.3.
                        update {
                            it.copy(report = it.report.copy(unlockAvailable = false), busy = null)
                        }
                        PlusErrorRouter.route(error, RESULTS_PAYWALL_SOURCE)?.let {
                            _effects.send(ResultsEffect.OpenLimit(it))
                        }
                    } else {
                        update { it.copy(busy = null, notice = ResultsNotice.Failed(error)) }
                    }
                }
            }
        }
    }

    fun onConnect() = decide(connect = true)

    fun onDecline() = decide(connect = false)

    private fun decide(connect: Boolean) {
        val content = loaded() ?: return
        if (content.busy != null || content.decision != DecisionState.PENDING) return
        update { it.copy(busy = Busy.DECIDE, notice = null) }
        viewModelScope.launch {
            when (val result = repository.decide(simulationId, connect)) {
                is ApiResult.Success -> {
                    val decided = result.value
                    val decision = when (decided.status) {
                        DecisionStatus.WAITING -> DecisionState.WAITING
                        DecisionStatus.CONNECTED -> DecisionState.CONNECTED
                        DecisionStatus.DECLINED -> DecisionState.DECLINED
                    }
                    update {
                        it.copy(
                            decision = decision,
                            chatId = decided.chatId ?: it.chatId,
                            report = it.report.copy(myDecision = connect),
                            busy = null
                        )
                    }
                    if (decision == DecisionState.CONNECTED) {
                        // Con la conexión mutua el servidor ya revela el nombre: se refresca el
                        // informe.
                        (
                            repository.report(
                                simulationId
                            ) as? ApiResult.Success
                            )?.value?.let { fresh ->
                            update { it.copy(report = fresh) }
                        }
                        decided.chatId?.let { _effects.send(ResultsEffect.OpenChat(it)) }
                    }
                }
                is ApiResult.Failure -> {
                    if (result.error is AppError.Conflict) {
                        // Ya había decidido (otro dispositivo): se muestra el estado real.
                        refreshReport()
                    } else {
                        update { it.copy(busy = null, notice = ResultsNotice.Failed(result.error)) }
                    }
                }
            }
        }
    }

    private suspend fun refreshReport() {
        when (val result = repository.report(simulationId)) {
            is ApiResult.Success -> update {
                it.copy(
                    report = result.value,
                    decision = decisionOf(result.value),
                    chatId = result.value.chatId,
                    busy = null
                )
            }
            is ApiResult.Failure -> update {
                it.copy(busy = null, notice = ResultsNotice.Failed(result.error))
            }
        }
    }

    /** 'Ver la conversación de los gemelos' (solo con el informe completo). */
    fun onOpenTranscript() {
        val content = loaded() ?: return
        if (!content.full) return
        update { it.copy(transcript = TranscriptState.Loading) }
        viewModelScope.launch {
            val next = when (val result = repository.transcript(simulationId)) {
                is ApiResult.Success -> TranscriptState.Loaded(result.value)
                is ApiResult.Failure -> TranscriptState.Error(result.error)
            }
            update { if (it.transcript != null) it.copy(transcript = next) else it }
        }
    }

    fun onCloseTranscript() = update { it.copy(transcript = null) }

    fun onDismissNotice() = update { it.copy(notice = null) }

    fun onSeePlus() {
        viewModelScope.launch { _effects.send(ResultsEffect.OpenPaywall) }
    }

    fun onOpenChat() {
        val chatId = loaded()?.chatId ?: return
        viewModelScope.launch { _effects.send(ResultsEffect.OpenChat(chatId)) }
    }

    private fun loaded(): ResultsContent? = (_state.value as? ResultsUiState.Loaded)?.content

    private fun update(transform: (ResultsContent) -> ResultsContent) {
        _state.update { current ->
            if (current is ResultsUiState.Loaded) {
                ResultsUiState.Loaded(
                    transform(current.content)
                )
            } else {
                current
            }
        }
    }

    companion object {
        private val MONTHS = listOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
        )

        /** Estado de la decisión a partir del informe (al abrir la pantalla o al volver). */
        fun decisionOf(report: ReportDto): DecisionState = when (report.myDecision) {
            null -> DecisionState.PENDING
            false -> DecisionState.DECLINED
            true -> when {
                report.chatId != null || report.otherName != null -> DecisionState.CONNECTED
                else -> DecisionState.WAITING
            }
        }

        /**
         * Mes en curso y mes en que vuelve el informe gratis, en hora de Bogotá. [resetsAt] es el
         * inicio del mes siguiente (QuotaService, E4.1): un milisegundo antes cae en el mes en
         * curso.
         */
        fun monthsOf(resetsAt: Instant): Pair<String, String> {
            val zone = TimeZone.getTimeZone("America/Bogota")
            fun month(millis: Long) =
                Calendar.getInstance(zone).apply { timeInMillis = millis }.get(Calendar.MONTH)
            val millis = resetsAt.toEpochMilliseconds()
            return MONTHS[month(millis - 1)] to MONTHS[month(millis)]
        }
    }
}
