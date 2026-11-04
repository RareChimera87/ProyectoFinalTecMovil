package me.santiagoreina.parcheu.feature.basicprofile

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
import me.santiagoreina.parcheu.contract.CatalogDto
import me.santiagoreina.parcheu.contract.IsoDate
import me.santiagoreina.parcheu.contract.MeDto
import me.santiagoreina.parcheu.contract.OnboardingStep
import me.santiagoreina.parcheu.contract.UpdateMe
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.auth.AuthRepository
import me.santiagoreina.parcheu.data.auth.destination
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.TwinQuestions
import me.santiagoreina.parcheu.navigation.Underage

/** Límites de B0 (iguales a los del servidor). */
object BasicProfileLimits {
    const val MAX_CAREER = 60
    const val MAX_NAME = 40
    const val MIN_INTERESTS = 1
    const val MAX_INTERESTS = 5
}

/**
 * Lo que la persona escribe en B0. [birthDateLocked] cuando la fecha ya está guardada en el servidor (es
 * inmutable: modo edición o un reintento tras fallar el `PATCH`). [askName] solo si Google no dio nombre.
 */
data class BasicProfileForm(
    val birthDate: IsoDate? = null,
    val birthDateLocked: Boolean = false,
    val universityId: Int? = null,
    val career: String = "",
    val interests: List<String> = emptyList(),
    val askName: Boolean = false,
    val displayName: String = ""
) {
    val careerTooLong: Boolean get() = career.trim().length > BasicProfileLimits.MAX_CAREER

    /** 'Continuar' exige fecha, universidad, de 1 a 5 intereses y (si se pide) nombre. */
    val isComplete: Boolean
        get() = birthDate != null &&
            universityId != null &&
            interests.size in BasicProfileLimits.MIN_INTERESTS..BasicProfileLimits.MAX_INTERESTS &&
            !careerTooLong &&
            (
                !askName ||
                    displayName.isNotBlank() &&
                    displayName.trim().length <= BasicProfileLimits.MAX_NAME
                )
}

sealed interface BasicProfileUiState {
    data object Loading : BasicProfileUiState

    /** No se pudo cargar el catálogo o la cuenta: se ofrece reintentar. */
    data class LoadError(val error: AppError) : BasicProfileUiState

    /**
     * Formulario. [edit] es el modo edición de C3 (E9.1): fecha de solo lectura y 'Guardar'. [error] es el
     * último fallo al guardar (se muestra sobre el botón).
     */
    data class Form(
        val catalog: CatalogDto,
        val form: BasicProfileForm,
        val edit: Boolean,
        val saving: Boolean = false,
        val error: AppError? = null
    ) : BasicProfileUiState {
        val canSubmit: Boolean get() = form.isComplete && !saving
    }
}

sealed interface BasicProfileEffect {
    /** Deja [route] como única pantalla (siguiente paso del onboarding o bloqueo por edad). */
    data class ReplaceAll(val route: AppRoute) : BasicProfileEffect

    /** Modo edición guardado: vuelve a C3. */
    data object Back : BasicProfileEffect
}

/**
 * B0 'Tu perfil' (E2.6, ADR-0031). En el onboarding: `POST /v1/me/age-verification` (si la fecha aún no
 * está guardada) → `PATCH /v1/me` → `POST /v1/session` para saber el siguiente paso. Un menor de edad
 * (403 UNDERAGE) va a la pantalla de bloqueo. En modo edición solo se hace el `PATCH`.
 */
class BasicProfileViewModel(
    private val edit: Boolean,
    private val repository: BasicProfileRepository,
    private val auth: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow<BasicProfileUiState>(BasicProfileUiState.Loading)
    val state: StateFlow<BasicProfileUiState> = _state.asStateFlow()

    private val _effects = Channel<BasicProfileEffect>(Channel.BUFFERED)
    val effects: Flow<BasicProfileEffect> = _effects.receiveAsFlow()

    init {
        load()
    }

    fun onRetryLoad() {
        if (_state.value is BasicProfileUiState.LoadError) load()
    }

    fun onBirthDateSelected(date: IsoDate) = updateForm {
        if (it.birthDateLocked) it else it.copy(birthDate = date)
    }

    fun onUniversitySelected(id: Int) = updateForm { it.copy(universityId = id) }

    fun onCareerChange(value: String) = updateForm { it.copy(career = value) }

    fun onDisplayNameChange(value: String) = updateForm { it.copy(displayName = value) }

    /** Marca o desmarca un interés; nunca deja pasar de [BasicProfileLimits.MAX_INTERESTS]. */
    fun onInterestToggle(slug: String) = updateForm {
        when {
            slug in it.interests -> it.copy(interests = it.interests - slug)
            it.interests.size >= BasicProfileLimits.MAX_INTERESTS -> it
            else -> it.copy(interests = it.interests + slug)
        }
    }

    fun onSubmit() {
        val current = _state.value as? BasicProfileUiState.Form ?: return
        if (!current.canSubmit) return
        _state.value = current.copy(saving = true, error = null)
        viewModelScope.launch { submit(current.form) }
    }

    private suspend fun submit(form: BasicProfileForm) {
        val birthDate = form.birthDate ?: return
        if (!form.birthDateLocked) {
            when (val verified = repository.verifyAge(birthDate)) {
                is ApiResult.Failure -> return when (verified.error) {
                    AppError.Underage -> _effects.send(BasicProfileEffect.ReplaceAll(Underage))
                    // La fecha ya estaba guardada (otro dispositivo): se recarga la verdadera y se bloquea el campo.
                    AppError.Conflict -> reloadAfterConflict()
                    else -> failSave(verified.error)
                }
                is ApiResult.Success -> updateForm { it.copy(birthDateLocked = true) }
            }
        }
        val request =
            UpdateMe(
                displayName = form.displayName.trim().takeIf { form.askName },
                career = form.career.trim(),
                universityId = form.universityId,
                interests = form.interests
            )
        when (val saved = repository.update(request)) {
            is ApiResult.Failure -> return failSave(saved.error)
            is ApiResult.Success -> Unit
        }
        if (edit) {
            _effects.send(BasicProfileEffect.Back)
            return
        }
        when (val session = auth.refreshSession()) {
            is ApiResult.Failure ->
                if (session.error == AppError.Underage) {
                    _effects.send(BasicProfileEffect.ReplaceAll(Underage))
                } else {
                    failSave(session.error)
                }
            is ApiResult.Success -> {
                val step = session.value.onboarding
                // Si el servidor aún pide el perfil (no debería) se sigue a B1 en lugar de quedar en bucle.
                val next = if (step ==
                    OnboardingStep.NEEDS_PROFILE
                ) {
                    TwinQuestions
                } else {
                    step.destination()
                }
                _effects.send(BasicProfileEffect.ReplaceAll(next))
            }
        }
    }

    private fun load() {
        _state.value = BasicProfileUiState.Loading
        viewModelScope.launch {
            val catalog =
                when (val result = repository.catalog()) {
                    is ApiResult.Failure -> return@launch onLoadFailure(result.error)
                    is ApiResult.Success -> result.value
                }
            val me =
                when (val result = repository.me()) {
                    is ApiResult.Failure -> return@launch onLoadFailure(result.error)
                    is ApiResult.Success -> result.value
                }
            if (me.underage) {
                _effects.send(BasicProfileEffect.ReplaceAll(Underage))
                return@launch
            }
            _state.value = BasicProfileUiState.Form(catalog, me.toForm(catalog), edit)
        }
    }

    private suspend fun onLoadFailure(error: AppError) {
        if (error == AppError.Underage) {
            _effects.send(BasicProfileEffect.ReplaceAll(Underage))
        } else {
            _state.value = BasicProfileUiState.LoadError(error)
        }
    }

    private suspend fun reloadAfterConflict() {
        when (val me = repository.me()) {
            is ApiResult.Success -> {
                val saved = me.value.birthDate
                updateForm { it.copy(birthDate = saved, birthDateLocked = saved != null) }
                failSave(AppError.Conflict)
            }
            is ApiResult.Failure -> failSave(AppError.Conflict)
        }
    }

    private fun failSave(error: AppError) {
        _state.update {
            (it as? BasicProfileUiState.Form)?.copy(saving = false, error = error) ?: it
        }
    }

    private fun updateForm(transform: (BasicProfileForm) -> BasicProfileForm) {
        _state.update {
            if (it is BasicProfileUiState.Form) {
                it.copy(
                    form = transform(it.form),
                    error = null
                )
            } else {
                it
            }
        }
    }
}

/** Datos ya guardados → formulario (modo edición o regreso a B0 a mitad del onboarding). */
internal fun MeDto.toForm(catalog: CatalogDto): BasicProfileForm {
    val known = catalog.interests.map { it.slug }.toSet()
    return BasicProfileForm(
        birthDate = birthDate,
        birthDateLocked = birthDate != null,
        universityId = universityId?.takeIf { id -> catalog.universities.any { it.id == id } },
        career = career.orEmpty(),
        interests = interests.filter { it in known }.take(BasicProfileLimits.MAX_INTERESTS),
        askName = displayName.isNullOrBlank(),
        displayName = displayName.orEmpty()
    )
}
