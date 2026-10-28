package me.santiagoreina.parcheu.feature.conocer.scenario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.ScenarioSessionDto
import me.santiagoreina.parcheu.contract.ScenarioSessionStatus
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

/** Estado de la tarjeta fijada y del acuerdo de F3. */
data class ScenarioUiState(
    val session: ScenarioSessionDto? = null,
    /** Segundos que faltan para `endsAt` (0 si ya venció o aún no se conoce). */
    val remainingSec: Long = 0,
    /** Duración total del temporizador (lo que quedaba al cargar la sesión); la barra la usa de base. */
    val totalSec: Long = 0,
    val agreeing: Boolean = false,
    val loadError: AppError? = null,
    val actionError: AppError? = null
) {
    val status: ScenarioSessionStatus? get() = session?.status

    /** El tiempo o el acuerdo cerraron el escenario (ya no se escribe). */
    val finished: Boolean
        get() = status == ScenarioSessionStatus.JUDGING || status == ScenarioSessionStatus.DONE

    /** Se puede proponer el acuerdo: en curso y sin haberlo marcado. */
    val canAgree: Boolean get() = status == ScenarioSessionStatus.OPEN && !agreeing &&
        remainingSec > 0
}

sealed interface ScenarioEffect {
    /** El juez terminó: abre el resultado (F7/F8). */
    data class OpenResults(val simulationId: String) : ScenarioEffect
}

/**
 * F3 · Modo escenario (E4.8). La ruta llama a [onTick] cada segundo (temporizador, con [now]) y a [poll] cada
 * [POLL_INTERVAL_MS] (acuerdo de la otra persona, cierre y resultado) desde un `LaunchedEffect`, así los bucles
 * viven con la pantalla y el ViewModel no tiene tareas infinitas. Con `DONE` y `simulationId` abre el resultado.
 */
class ScenarioViewModel(
    private val chatId: String,
    private val repository: ScenarioRepository,
    private val now: () -> Instant = { Clock.System.now() }
) : ViewModel() {
    private val _state = MutableStateFlow(ScenarioUiState())
    val state: StateFlow<ScenarioUiState> = _state.asStateFlow()

    private val _effects = Channel<ScenarioEffect>(Channel.BUFFERED)
    val effects: Flow<ScenarioEffect> = _effects.receiveAsFlow()

    private var opened = false

    init {
        refresh()
    }

    /** Recalcula el tiempo restante (una vez por segundo). */
    fun onTick() {
        _state.update { s -> s.session?.let { s.copy(remainingSec = remaining(it)) } ?: s }
    }

    /** Vuelve a leer la sesión mientras no esté `DONE` (el resultado ya no cambia). */
    fun poll() {
        if (_state.value.status != ScenarioSessionStatus.DONE) refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = repository.session(chatId)) {
                is ApiResult.Success -> apply(result.value)
                is ApiResult.Failure -> _state.update { it.copy(loadError = result.error) }
            }
        }
    }

    /** 'Llegamos a un acuerdo'. */
    fun onAgree() {
        val current = _state.value
        if (!current.canAgree) return
        _state.value = current.copy(agreeing = true, actionError = null)
        viewModelScope.launch {
            when (val result = repository.agree(chatId)) {
                is ApiResult.Success -> apply(result.value)
                is ApiResult.Failure -> {
                    _state.update { it.copy(agreeing = false, actionError = result.error) }
                    // 409 SCENARIO_ENDED: el tiempo venció; se vuelve a leer para mostrar el cierre.
                    if (result.error == AppError.ScenarioEnded) refresh()
                }
            }
        }
    }

    fun onOpenResults() {
        val id = _state.value.session?.simulationId ?: return
        viewModelScope.launch { _effects.send(ScenarioEffect.OpenResults(id)) }
    }

    private suspend fun apply(session: ScenarioSessionDto) {
        _state.update {
            it.copy(
                session = session,
                agreeing = false,
                loadError = null,
                remainingSec = remaining(session),
                totalSec = maxOf(it.totalSec, remaining(session))
            )
        }
        val id = session.simulationId
        if (session.status == ScenarioSessionStatus.DONE && id != null && !opened) {
            opened = true
            _effects.send(ScenarioEffect.OpenResults(id))
        }
    }

    private fun remaining(session: ScenarioSessionDto): Long =
        session.endsAt?.let { (it - now()).inWholeSeconds.coerceAtLeast(0) } ?: 0

    companion object {
        const val POLL_INTERVAL_MS = 3_000L
    }
}
