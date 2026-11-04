package me.santiagoreina.parcheu.feature.traintwin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.FeedbackKind
import me.santiagoreina.parcheu.contract.FeedbackPrompt
import me.santiagoreina.parcheu.contract.MemoryDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

/** Códigos de respuesta de la pregunta de control, en el mismo orden que `options` del servidor. */
val CONTROL_ANSWER_CODES = listOf("A", "B", "DEPENDS")

/** Memorias que se muestran de cada lista antes de tocar 'Ver todas'. */
const val MEMORIES_PREVIEW = 3

/** Contenido de C4. [busy] son los ids con una petición en vuelo; [message] un error pasajero. */
data class TrainTwinContent(
    val pending: List<MemoryDto> = emptyList(),
    val accepted: List<MemoryDto> = emptyList(),
    val prompts: List<FeedbackPrompt> = emptyList(),
    val showAll: Boolean = false,
    val busy: Set<String> = emptySet(),
    val message: String? = null
) {
    val control: FeedbackPrompt? get() = prompts.firstOrNull { it.kind == FeedbackKind.CONTROL }
    val ratings: List<FeedbackPrompt> get() = prompts.filter { it.kind == FeedbackKind.RATING }
    val visiblePending: List<MemoryDto> get() = if (showAll) {
        pending
    } else {
        pending.take(
            MEMORIES_PREVIEW
        )
    }
    val visibleAccepted: List<MemoryDto> get() = if (showAll) {
        accepted
    } else {
        accepted.take(
            MEMORIES_PREVIEW
        )
    }

    /** 'Ver todas' solo aparece si hay algo más que mostrar o ya está expandido. */
    val canExpand: Boolean
        get() = showAll || pending.size > MEMORIES_PREVIEW || accepted.size > MEMORIES_PREVIEW
}

/** Estados de C4: cargando, error y con contenido. */
sealed interface TrainTwinUiState {
    data object Loading : TrainTwinUiState

    data class Error(val error: AppError) : TrainTwinUiState

    data class Loaded(val content: TrainTwinContent) : TrainTwinUiState
}

/** Referencia a un turno de una simulación: el `promptId` de una calificación es `<simulationId>:<turnIndex>`. */
data class TurnRef(val simulationId: String, val turnIndex: Int)

fun parseTurnRef(promptId: String): TurnRef? {
    val parts = promptId.split(":")
    val index = parts.getOrNull(1)?.toIntOrNull()
    return if (parts.size == 2 && parts[0].isNotEmpty() &&
        index != null
    ) {
        TurnRef(parts[0], index)
    } else {
        null
    }
}

/** C4 (E9.3): memorias pendientes y aceptadas, pregunta de control y respuestas del gemelo por calificar. */
class TrainTwinViewModel(private val repository: TrainTwinRepository) : ViewModel() {
    private val _state = MutableStateFlow<TrainTwinUiState>(TrainTwinUiState.Loading)
    val state: StateFlow<TrainTwinUiState> = _state.asStateFlow()

    private var started = false

    /** Primera carga; repetirla no hace nada. */
    fun start() {
        if (started) return
        started = true
        load()
    }

    fun retry() {
        _state.value = TrainTwinUiState.Loading
        load()
    }

    fun onShowAll() = updateContent { it.copy(showAll = !it.showAll) }

    fun onAccept(memory: MemoryDto) = decide(memory, accept = true)

    fun onReject(memory: MemoryDto) = decide(memory, accept = false)

    fun onRate(promptId: String, positive: Boolean) = send(promptId) {
        repository.rate(promptId, positive)
    }

    /** [optionIndex] es la posición en `options` (A, B o Depende). */
    fun onControlAnswer(promptId: String, optionIndex: Int) {
        val code = CONTROL_ANSWER_CODES.getOrNull(optionIndex) ?: return
        send(promptId) { repository.answerControl(promptId, code) }
    }

    fun onDismissMessage() = updateContent { it.copy(message = null) }

    private fun load() {
        viewModelScope.launch {
            val result =
                coroutineScope {
                    val pending = async { repository.pendingMemories() }
                    val accepted = async { repository.acceptedMemories() }
                    val prompts = async { repository.prompts() }
                    Triple(pending.await(), accepted.await(), prompts.await())
                }
            val (pending, accepted, prompts) = result
            val failure =
                listOf(
                    pending,
                    accepted,
                    prompts
                ).filterIsInstance<ApiResult.Failure>().firstOrNull()
            _state.value =
                if (failure != null) {
                    TrainTwinUiState.Error(failure.error)
                } else {
                    TrainTwinUiState.Loaded(
                        TrainTwinContent(
                            pending = (pending as ApiResult.Success).value,
                            accepted = (accepted as ApiResult.Success).value,
                            prompts = (prompts as ApiResult.Success).value
                        )
                    )
                }
        }
    }

    private fun decide(memory: MemoryDto, accept: Boolean) {
        if (!markBusy(memory.id)) return
        viewModelScope.launch {
            val result = if (accept) repository.accept(memory.id) else repository.reject(memory.id)
            updateContent { content ->
                when (result) {
                    is ApiResult.Success ->
                        content.copy(
                            pending = content.pending.filterNot { it.id == memory.id },
                            accepted = if (accept) {
                                listOf(
                                    result.value
                                ) + content.accepted
                            } else {
                                content.accepted
                            },
                            busy = content.busy - memory.id
                        )
                    is ApiResult.Failure -> content.copy(
                        busy = content.busy - memory.id,
                        message = result.error.message()
                    )
                }
            }
        }
    }

    private fun send(promptId: String, call: suspend () -> ApiResult<*>) {
        if (!markBusy(promptId)) return
        viewModelScope.launch {
            val result = call()
            updateContent { content ->
                when (result) {
                    is ApiResult.Success ->
                        content.copy(
                            prompts = content.prompts.filterNot {
                                it.id == promptId
                            },
                            busy =
                            content.busy - promptId
                        )
                    is ApiResult.Failure -> content.copy(
                        busy = content.busy - promptId,
                        message = result.error.message()
                    )
                }
            }
        }
    }

    /** Marca [id] en vuelo; false si no hay contenido o ya estaba en vuelo (evita doble envío). */
    private fun markBusy(id: String): Boolean {
        val content = (_state.value as? TrainTwinUiState.Loaded)?.content ?: return false
        if (id in content.busy) return false
        updateContent { it.copy(busy = it.busy + id, message = null) }
        return true
    }

    private fun updateContent(transform: (TrainTwinContent) -> TrainTwinContent) {
        _state.update { current ->
            if (current is TrainTwinUiState.Loaded) {
                TrainTwinUiState.Loaded(
                    transform(current.content)
                )
            } else {
                current
            }
        }
    }
}

/** Mensaje de error de C4 (carga y acciones). */
fun AppError.message(): String = when (this) {
    AppError.Network -> "Revisa tu conexión e intenta de nuevo."
    AppError.Conflict -> "Esa ya la habías respondido."
    AppError.NotFound -> "Eso ya no está disponible."
    else -> "Algo salió mal. Intenta de nuevo en un momento."
}
