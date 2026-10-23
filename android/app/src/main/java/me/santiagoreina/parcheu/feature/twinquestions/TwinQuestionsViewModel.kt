package me.santiagoreina.parcheu.feature.twinquestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.Answer
import me.santiagoreina.parcheu.contract.Answers
import me.santiagoreina.parcheu.contract.QuestionDto
import me.santiagoreina.parcheu.contract.TwinAnswer
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

/** Fase de B1. [Done] cubre el instante entre el envío exitoso y la navegación a B2. */
enum class TwinQuestionsPhase { Loading, Answering, Submitting, Error, Done }

data class TwinQuestionsUiState(
    val phase: TwinQuestionsPhase = TwinQuestionsPhase.Loading,
    val questions: List<QuestionDto> = emptyList(),
    val answers: Map<Int, TwinAnswer> = emptyMap(),
    val error: AppError? = null
) {
    val total: Int get() = questions.size

    /** Cantidad de preguntas ya respondidas. */
    val answeredCount: Int get() = questions.count { it.id in answers }

    /** Primera pregunta sin respuesta, o `null` si ya se respondieron todas. */
    val current: QuestionDto? get() = questions.firstOrNull { it.id !in answers }

    /** Número (desde 1) de la pregunta que se muestra; con todas respondidas se queda en la última. */
    val currentNumber: Int get() = (answeredCount + 1).coerceAtMost(total.coerceAtLeast(1))
}

sealed interface TwinQuestionsEvent {
    /** Las respuestas se enviaron: seguir a B2. */
    data object Finished : TwinQuestionsEvent
}

/**
 * B1. Registra A, B o DEPENDS, guarda el avance en [AnswerProgressStore] tras cada respuesta y, al
 * responder la última, envía las respuestas una sola vez. Si el envío falla, el avance sigue guardado y
 * [retry] reintenta.
 */
class TwinQuestionsViewModel(
    private val repository: TwinQuestionsRepository,
    private val store: AnswerProgressStore
) : ViewModel() {
    private val _state = MutableStateFlow(TwinQuestionsUiState())
    val state: StateFlow<TwinQuestionsUiState> = _state.asStateFlow()

    private val _events = Channel<TwinQuestionsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    private fun load() {
        _state.update { it.copy(phase = TwinQuestionsPhase.Loading, error = null) }
        viewModelScope.launch {
            when (val result = repository.questions()) {
                is ApiResult.Failure -> _state.update {
                    it.copy(phase = TwinQuestionsPhase.Error, error = result.error)
                }
                is ApiResult.Success -> {
                    val questions = result.value
                    val ids = questions.map { it.id }.toSet()
                    val saved = store.load().filterKeys { it in ids }
                    _state.update {
                        it.copy(
                            phase = TwinQuestionsPhase.Answering,
                            questions = questions,
                            answers = saved,
                            error = null
                        )
                    }
                    // Retoma con todo respondido (la app se cerró durante un envío fallido).
                    if (questions.isNotEmpty() && saved.size == questions.size) submit()
                }
            }
        }
    }

    /** Registra la respuesta a la pregunta actual. Ignorada si no se está respondiendo. */
    fun answer(answer: TwinAnswer) {
        val current = _state.value
        if (current.phase != TwinQuestionsPhase.Answering) return
        val question = current.current ?: return
        val updated = current.answers + (question.id to answer)
        _state.value = current.copy(answers = updated)
        viewModelScope.launch {
            store.save(updated)
            if (updated.size == current.total) submit()
        }
    }

    fun retry() {
        val current = _state.value
        when {
            current.phase != TwinQuestionsPhase.Error -> Unit
            current.questions.isEmpty() -> load()
            else -> submit()
        }
    }

    private fun submit() {
        val current = _state.value
        // Un solo envío en vuelo: la fase Submitting (o Done) cierra la puerta a cualquier duplicado.
        if (current.phase == TwinQuestionsPhase.Submitting ||
            current.phase == TwinQuestionsPhase.Done
        ) {
            return
        }
        _state.value = current.copy(phase = TwinQuestionsPhase.Submitting, error = null)
        val items = current.questions.mapNotNull { q ->
            current.answers[q.id]?.let { Answer(q.id, it) }
        }
        viewModelScope.launch {
            when (val result = repository.submit(Answers(items))) {
                is ApiResult.Success -> {
                    store.clear()
                    _state.update { it.copy(phase = TwinQuestionsPhase.Done) }
                    _events.send(TwinQuestionsEvent.Finished)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(phase = TwinQuestionsPhase.Error, error = result.error)
                }
            }
        }
    }
}
