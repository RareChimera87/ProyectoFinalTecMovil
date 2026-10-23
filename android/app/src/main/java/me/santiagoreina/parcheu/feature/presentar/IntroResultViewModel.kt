package me.santiagoreina.parcheu.feature.presentar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.IntroductionDto
import me.santiagoreina.parcheu.contract.IntroductionStatus
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.results.ResultsViewModel

/** Lo que está en curso en E2. */
enum class IntroResultBusy { UNLOCK, SEND, SKIP }

sealed interface IntroResultNotice {
    /** El informe del mes ya se usó (otro dispositivo u otra pantalla). */
    data object ReportAlreadyUsed : IntroResultNotice

    data class Failed(val error: AppError) : IntroResultNotice
}

/**
 * E2 para quien presenta. [usedMonth] y [resetMonth] salen del cupo de informe completo
 * (mismo texto que F7); son nulos si no se pudo leer.
 */
data class IntroResultContent(
    val introduction: IntroductionDto,
    val plus: Boolean = false,
    val usedMonth: String? = null,
    val resetMonth: String? = null,
    val busy: IntroResultBusy? = null,
    val notice: IntroResultNotice? = null
) {
    val status: IntroductionStatus get() = introduction.status

    /** Las dos personas presentadas, en el orden en que se eligieron. */
    val names: Pair<String, String>
        get() = introduction.introduced.let {
            (it.getOrNull(0)?.label ?: introduction.other.label) to (it.getOrNull(1)?.label ?: "")
        }
}

sealed interface IntroResultUiState {
    data object Loading : IntroResultUiState

    data class Loaded(val content: IntroResultContent) : IntroResultUiState

    data class Error(val error: AppError) : IntroResultUiState
}

sealed interface IntroResultEffect {
    data object Close : IntroResultEffect

    data object OpenPaywall : IntroResultEffect

    data object IntroduceAgain : IntroResultEffect
}

/**
 * E2 · Resultado de sus gemelos (E6.2). Mientras la simulación corre consulta cada
 * [pollMillis]; al terminar quien presenta decide si envía la propuesta. El informe sigue la
 * regla del informe mensual.
 */
class IntroResultViewModel(
    private val introductionId: String,
    private val repository: IntroductionsRepository,
    private val pollMillis: Long = POLL_MILLIS
) : ViewModel() {
    private val _state = MutableStateFlow<IntroResultUiState>(IntroResultUiState.Loading)
    val state: StateFlow<IntroResultUiState> = _state.asStateFlow()

    private val _effects = Channel<IntroResultEffect>(Channel.BUFFERED)
    val effects: Flow<IntroResultEffect> = _effects.receiveAsFlow()

    private var polling: Job? = null

    init {
        load()
    }

    fun onRetry() = load()

    private fun load() {
        _state.value = IntroResultUiState.Loading
        viewModelScope.launch {
            val intro = async { repository.introduction(introductionId) }
            val quota = async { repository.quota() }
            when (val result = intro.await()) {
                is ApiResult.Failure -> _state.value = IntroResultUiState.Error(result.error)
                is ApiResult.Success -> {
                    val quotaDto = (quota.await() as? ApiResult.Success)?.value
                    val months = quotaDto?.let { ResultsViewModel.monthsOf(it.fullReport.resetsAt) }
                    _state.value = IntroResultUiState.Loaded(
                        IntroResultContent(
                            introduction = result.value,
                            plus = quotaDto?.plus ?: false,
                            usedMonth = months?.first,
                            resetMonth = months?.second
                        )
                    )
                    pollWhileSimulating()
                }
            }
        }
    }

    private fun pollWhileSimulating() {
        if (loaded()?.status != IntroductionStatus.SIMULATING || polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (loaded()?.status == IntroductionStatus.SIMULATING) {
                delay(pollMillis)
                val fresh =
                    (repository.introduction(introductionId) as? ApiResult.Success)?.value
                        ?: continue
                update { it.copy(introduction = fresh) }
            }
        }
    }

    /** 'Ver informe completo' con el informe gratis del mes. */
    fun onUnlock() {
        val content = loaded() ?: return
        val report = content.introduction.report ?: return
        val simulationId = content.introduction.simulationId ?: return
        if (content.busy != null || !report.locked) return
        update { it.copy(busy = IntroResultBusy.UNLOCK, notice = null) }
        viewModelScope.launch {
            when (val result = repository.unlock(simulationId)) {
                is ApiResult.Success -> update {
                    it.copy(introduction = it.introduction.copy(report = result.value), busy = null)
                }
                is ApiResult.Failure -> {
                    val error = result.error
                    if (error is AppError.QuotaExceeded && error.kind == QuotaKind.FULL_REPORT) {
                        update {
                            it.copy(
                                introduction = it.introduction.copy(
                                    report = report.copy(unlockAvailable = false)
                                ),
                                busy = null,
                                notice = IntroResultNotice.ReportAlreadyUsed
                            )
                        }
                    } else {
                        update { it.copy(busy = null, notice = IntroResultNotice.Failed(error)) }
                    }
                }
            }
        }
    }

    /** 'Enviar propuesta a los dos'. */
    fun onSend() = decide(send = true)

    /** 'No por ahora'. */
    fun onSkip() = decide(send = false)

    private fun decide(send: Boolean) {
        val content = loaded() ?: return
        if (content.busy != null || content.status != IntroductionStatus.READY) return
        update {
            it.copy(busy = if (send) IntroResultBusy.SEND else IntroResultBusy.SKIP, notice = null)
        }
        viewModelScope.launch {
            when (val result = repository.respond(introductionId, send)) {
                is ApiResult.Success -> {
                    update { it.copy(introduction = result.value, busy = null) }
                    if (!send) _effects.send(IntroResultEffect.Close)
                }
                is ApiResult.Failure -> {
                    if (result.error is AppError.Conflict) {
                        // Ya se decidió (otro dispositivo): se muestra el estado real.
                        val fresh = (
                            repository.introduction(
                                introductionId
                            ) as? ApiResult.Success
                            )?.value
                        update { it.copy(introduction = fresh ?: it.introduction, busy = null) }
                    } else {
                        update {
                            it.copy(busy = null, notice = IntroResultNotice.Failed(result.error))
                        }
                    }
                }
            }
        }
    }

    fun onSeePlus() {
        viewModelScope.launch { _effects.send(IntroResultEffect.OpenPaywall) }
    }

    fun onIntroduceAgain() {
        viewModelScope.launch { _effects.send(IntroResultEffect.IntroduceAgain) }
    }

    fun onDone() {
        viewModelScope.launch { _effects.send(IntroResultEffect.Close) }
    }

    fun onDismissNotice() = update { it.copy(notice = null) }

    private fun loaded(): IntroResultContent? =
        (_state.value as? IntroResultUiState.Loaded)?.content

    private fun update(transform: (IntroResultContent) -> IntroResultContent) {
        _state.update { current ->
            if (current is IntroResultUiState.Loaded) {
                IntroResultUiState.Loaded(
                    transform(current.content)
                )
            } else {
                current
            }
        }
    }

    companion object {
        /** Cada cuánto se consulta mientras simulan (también llega un aviso al terminar). */
        const val POLL_MILLIS = 4_000L
    }
}
