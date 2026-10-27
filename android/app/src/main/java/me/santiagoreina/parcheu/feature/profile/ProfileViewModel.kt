package me.santiagoreina.parcheu.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.TwinDto
import me.santiagoreina.parcheu.contract.TwinPoint
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.navigation.AddFriends
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.BasicProfile
import me.santiagoreina.parcheu.navigation.ManageSubscription
import me.santiagoreina.parcheu.navigation.PrivacySettings
import me.santiagoreina.parcheu.navigation.TrainTwin

/**
 * Gráfico 'Qué tanto te conoce' (C3). Los puntos son semanales; el estado depende de cuántos tienen
 * precisión: ninguno → [Empty], uno → [Single] (valor sin línea), dos o más → [Line].
 */
sealed interface TwinChart {
    data object Empty : TwinChart

    /** `GET /v1/twin/evolution` falló: no se afirma que el gemelo "aún aprende". */
    data object Unavailable : TwinChart

    data class Single(val value: Int) : TwinChart

    data class Line(val values: List<Int>) : TwinChart {
        val last: Int get() = values.last()
    }
}

/** Convierte los puntos del servidor (ya ordenados por semana) en el estado del gráfico. */
fun chartOf(points: List<TwinPoint>): TwinChart {
    val values = points.mapNotNull { it.accuracy }
    return when (values.size) {
        0 -> TwinChart.Empty
        1 -> TwinChart.Single(values.first())
        else -> TwinChart.Line(values)
    }
}

sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data class Loaded(
        val me: MeDto,
        val twin: TwinDto,
        val chart: TwinChart,
        val universityName: String?
    ) : ProfileUiState

    data class Error(val error: AppError) : ProfileUiState
}

sealed interface ProfileEffect {
    data class Navigate(val route: AppRoute) : ProfileEffect
}

/**
 * C3 (E9.1). Carga cuenta, gemelo y evolución en paralelo. Sin cuenta o sin gemelo no hay pantalla
 * útil (error con reintento); la evolución y el catálogo son best effort. 'Editar perfil' abre B0 en
 * modo
 * edición ([BasicProfile.edit]); la fecha de nacimiento y el `PATCH /v1/me` los resuelve B0.
 */
class ProfileViewModel(private val repository: ProfileRepository) : ViewModel() {
    private val _state = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private val _effects = Channel<ProfileEffect>(Channel.BUFFERED)
    val effects: Flow<ProfileEffect> = _effects.receiveAsFlow()

    /** Carga o refresca; con datos en pantalla un fallo no los reemplaza. */
    fun load() {
        val current = _state.value
        if (current !is ProfileUiState.Loaded) _state.value = ProfileUiState.Loading
        viewModelScope.launch {
            coroutineScope {
                val me = async { repository.me() }
                val twin = async { repository.twin() }
                val evolution = async { repository.evolution() }
                val catalog = async { repository.catalog() }
                val meResult = me.await()
                val twinResult = twin.await()
                val evolutionResult = evolution.await()
                val catalogResult = catalog.await()
                if (meResult is ApiResult.Success && twinResult is ApiResult.Success) {
                    val chart =
                        when (evolutionResult) {
                            is ApiResult.Success -> chartOf(evolutionResult.value)
                            is ApiResult.Failure -> TwinChart.Unavailable
                        }
                    val university =
                        (catalogResult as? ApiResult.Success)
                            ?.value
                            ?.universities
                            ?.firstOrNull { it.id == meResult.value.universityId }
                            ?.name
                    _state.value =
                        ProfileUiState.Loaded(meResult.value, twinResult.value, chart, university)
                } else if (current !is ProfileUiState.Loaded) {
                    val error =
                        (meResult as? ApiResult.Failure)?.error
                            ?: (twinResult as ApiResult.Failure).error
                    _state.value = ProfileUiState.Error(error)
                }
            }
        }
    }

    fun onEditProfile() = navigate(BasicProfile(edit = true))

    fun onTrainTwin() = navigate(TrainTwin)

    fun onSettings() = navigate(PrivacySettings)

    fun onSubscription() = navigate(ManageSubscription)

    fun onAddFriends() = navigate(AddFriends())

    private fun navigate(route: AppRoute) {
        _effects.trySend(ProfileEffect.Navigate(route))
    }
}
