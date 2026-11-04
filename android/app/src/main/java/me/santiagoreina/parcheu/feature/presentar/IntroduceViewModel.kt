package me.santiagoreina.parcheu.feature.presentar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.QuotaDto
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult

/** Modos en los que se puede presentar (Pareja nunca: ADR-0012). */
val INTRODUCE_MODES = listOf(Mode.AMISTAD, Mode.ESTUDIO)

/** Cuántas personas se eligen en E1. */
const val INTRODUCE_PICK = 2

/** Avisos de E1. */
sealed interface IntroduceNotice {
    /** 402 QUOTA_EXCEEDED: ya no quedan simulaciones esta semana. */
    data object QuotaExceeded : IntroduceNotice

    data class Failed(val error: AppError) : IntroduceNotice
}

/**
 * E1. [simulationsLeft] es nulo si el cupo no se pudo leer (el servidor decide igual al presentar);
 * [selected] guarda los ids en el orden en que se eligieron (máximo [INTRODUCE_PICK]).
 */
data class IntroduceContent(
    val mode: Mode = Mode.AMISTAD,
    val friends: List<FriendOption> = emptyList(),
    val selected: List<String> = emptyList(),
    val simulationsLeft: Int? = null,
    val plus: Boolean = false,
    val loadingFriends: Boolean = false,
    val friendsError: AppError? = null,
    val busy: Boolean = false,
    val notice: IntroduceNotice? = null
) {
    val chosen: List<FriendOption> get() = selected.mapNotNull { id ->
        friends.firstOrNull {
            it.id ==
                id
        }
    }

    /** Sin simulaciones esta semana: el botón se cambia por ParcheU+. */
    val limitReached: Boolean get() = simulationsLeft == 0

    val canSubmit: Boolean get() = selected.size == INTRODUCE_PICK && !busy && !limitReached

    /** Con dos elegidos los demás quedan deshabilitados hasta quitar a alguien. */
    fun isSelectable(id: String): Boolean = id in selected || selected.size < INTRODUCE_PICK
}

sealed interface IntroduceEffect {
    data class OpenResult(val introductionId: String) : IntroduceEffect

    data object OpenPaywall : IntroduceEffect
}

/** E1 · Elegir modo y dos amigos (E6.2). */
class IntroduceViewModel(private val repository: IntroductionsRepository) : ViewModel() {
    private val _state = MutableStateFlow(IntroduceContent(loadingFriends = true))
    val state: StateFlow<IntroduceContent> = _state.asStateFlow()

    private val _effects = Channel<IntroduceEffect>(Channel.BUFFERED)
    val effects: Flow<IntroduceEffect> = _effects.receiveAsFlow()

    init {
        load(Mode.AMISTAD, withQuota = true)
    }

    fun onMode(mode: Mode) {
        if (mode !in INTRODUCE_MODES || mode == _state.value.mode) return
        _state.update {
            it.copy(mode = mode, selected = emptyList(), friends = emptyList(), notice = null)
        }
        load(mode, withQuota = false)
    }

    fun onRetry() = load(_state.value.mode, withQuota = _state.value.simulationsLeft == null)

    private fun load(mode: Mode, withQuota: Boolean) {
        _state.update { it.copy(loadingFriends = true, friendsError = null) }
        viewModelScope.launch {
            val quota = if (withQuota) async { repository.quota() } else null
            val friends = repository.friends(mode)
            val quotaDto = (quota?.await() as? ApiResult.Success)?.value
            _state.update { current ->
                if (current.mode != mode) return@update current
                val base = quotaDto?.let { current.withQuota(it) } ?: current
                when (friends) {
                    is ApiResult.Success -> base.copy(
                        friends = friends.value,
                        selected = base.selected.filter { id -> friends.value.any { it.id == id } },
                        loadingFriends = false
                    )
                    is ApiResult.Failure -> base.copy(
                        loadingFriends = false,
                        friendsError = friends.error
                    )
                }
            }
        }
    }

    fun onToggle(id: String) {
        _state.update { current ->
            when {
                current.busy -> current
                id in current.selected -> current.copy(selected = current.selected - id)
                current.selected.size >= INTRODUCE_PICK -> current
                current.friends.none { it.id == id } -> current
                else -> current.copy(selected = current.selected + id)
            }
        }
    }

    fun onSubmit() {
        val content = _state.value
        if (!content.canSubmit) return
        val (a, b) = content.selected
        _state.update { it.copy(busy = true, notice = null) }
        viewModelScope.launch {
            when (val result = repository.introduce(content.mode, a, b)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            busy = false,
                            simulationsLeft = it.simulationsLeft?.minus(1)?.coerceAtLeast(0)
                        )
                    }
                    _effects.send(IntroduceEffect.OpenResult(result.value.id))
                }
                is ApiResult.Failure -> {
                    val error = result.error
                    if (error is AppError.QuotaExceeded && error.kind != QuotaKind.FULL_REPORT) {
                        _state.update {
                            it.copy(
                                busy = false,
                                simulationsLeft = 0,
                                notice = IntroduceNotice.QuotaExceeded
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(busy = false, notice = IntroduceNotice.Failed(error))
                        }
                    }
                }
            }
        }
    }

    fun onSeePlus() {
        viewModelScope.launch { _effects.send(IntroduceEffect.OpenPaywall) }
    }

    fun onDismissNotice() = _state.update { it.copy(notice = null) }

    private fun IntroduceContent.withQuota(quota: QuotaDto): IntroduceContent {
        val usage = quota.simulations
        return copy(
            simulationsLeft = usage.limit?.let {
                (it - usage.used).coerceAtLeast(0)
            },
            plus = quota.plus
        )
    }
}
