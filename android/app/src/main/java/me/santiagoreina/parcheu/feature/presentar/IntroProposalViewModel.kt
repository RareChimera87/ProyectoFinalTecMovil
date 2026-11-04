package me.santiagoreina.parcheu.feature.presentar

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
import me.santiagoreina.parcheu.contract.IntroductionDto
import me.santiagoreina.parcheu.contract.IntroductionStatus
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

/** Estado de E3 visto por la persona que recibe la propuesta. */
enum class ProposalState {
    /** Aún no responde: 'Me interesa' / 'No, gracias'. */
    PENDING,

    /** Dijo 'Me interesa' y falta la otra persona (o la otra dijo que no: nunca se revela). */
    WAITING,

    /** Dijo 'No, gracias'. */
    DECLINED,

    /** Los dos dijeron que sí: hay chat. */
    CONNECTED
}

data class IntroProposalContent(
    val introduction: IntroductionDto,
    val busy: Boolean = false,
    val error: AppError? = null
) {
    val state: ProposalState
        get() = when {
            introduction.status == IntroductionStatus.CONNECTED -> ProposalState.CONNECTED
            introduction.myResponse == false -> ProposalState.DECLINED
            introduction.status == IntroductionStatus.DECLINED -> ProposalState.DECLINED
            introduction.myResponse == true -> ProposalState.WAITING
            else -> ProposalState.PENDING
        }

    val chatId: String? get() = introduction.report?.chatId
}

sealed interface IntroProposalUiState {
    data object Loading : IntroProposalUiState

    data class Loaded(val content: IntroProposalContent) : IntroProposalUiState

    data class Error(val error: AppError) : IntroProposalUiState
}

sealed interface IntroProposalEffect {
    data class OpenChat(val chatId: String) : IntroProposalEffect
}

/** E3 · Propuesta recibida (E6.2). */
class IntroProposalViewModel(
    private val introductionId: String,
    private val repository: IntroductionsRepository
) : ViewModel() {
    private val _state = MutableStateFlow<IntroProposalUiState>(IntroProposalUiState.Loading)
    val state: StateFlow<IntroProposalUiState> = _state.asStateFlow()

    private val _effects = Channel<IntroProposalEffect>(Channel.BUFFERED)
    val effects: Flow<IntroProposalEffect> = _effects.receiveAsFlow()

    init {
        load()
    }

    fun onRetry() = load()

    private fun load() {
        _state.value = IntroProposalUiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = repository.introduction(introductionId)) {
                is ApiResult.Success -> IntroProposalUiState.Loaded(
                    IntroProposalContent(result.value)
                )
                is ApiResult.Failure -> IntroProposalUiState.Error(result.error)
            }
        }
    }

    fun onInterested() = respond(true)

    fun onDecline() = respond(false)

    private fun respond(interested: Boolean) {
        val content = loaded() ?: return
        if (content.busy || content.state != ProposalState.PENDING) return
        update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.respond(introductionId, interested)) {
                is ApiResult.Success -> {
                    update { IntroProposalContent(result.value) }
                    val fresh = loaded()
                    if (fresh?.state == ProposalState.CONNECTED) {
                        fresh.chatId?.let { _effects.send(IntroProposalEffect.OpenChat(it)) }
                    }
                }
                is ApiResult.Failure -> {
                    if (result.error is AppError.Conflict) {
                        val fresh = (
                            repository.introduction(
                                introductionId
                            ) as? ApiResult.Success
                            )?.value
                        update { it.copy(introduction = fresh ?: it.introduction, busy = false) }
                    } else {
                        update { it.copy(busy = false, error = result.error) }
                    }
                }
            }
        }
    }

    fun onOpenChat() {
        val chatId = loaded()?.chatId ?: return
        viewModelScope.launch { _effects.send(IntroProposalEffect.OpenChat(chatId)) }
    }

    fun onDismissError() = update { it.copy(error = null) }

    private fun loaded(): IntroProposalContent? =
        (_state.value as? IntroProposalUiState.Loaded)?.content

    private fun update(transform: (IntroProposalContent) -> IntroProposalContent) {
        _state.update { current ->
            if (current is IntroProposalUiState.Loaded) {
                IntroProposalUiState.Loaded(
                    transform(current.content)
                )
            } else {
                current
            }
        }
    }
}
