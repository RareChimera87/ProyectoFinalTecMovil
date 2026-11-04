package me.santiagoreina.parcheu.core.ui

import me.santiagoreina.parcheu.core.error.AppError

/**
 * Estado base de una pantalla que carga datos (arquitectura.md §2.3). Una feature puede usarlo tal cual
 * (`StateFlow<UiState<HomeDto>>`) o declarar su propio `XUiState` con la misma forma.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Loaded<T>(val data: T) : UiState<T>

    data object Empty : UiState<Nothing>

    data class Error(val error: AppError) : UiState<Nothing>
}

/** Convierte un resultado en estado: lista o colección vacía → [UiState.Empty]. */
fun <T> T.toUiState(
    isEmpty: (T) -> Boolean = { (it as? Collection<*>)?.isEmpty() == true }
): UiState<T> = if (isEmpty(this)) UiState.Empty else UiState.Loaded(this)
