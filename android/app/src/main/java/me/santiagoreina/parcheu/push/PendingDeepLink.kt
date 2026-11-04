package me.santiagoreina.parcheu.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.santiagoreina.parcheu.navigation.AppRoute

/**
 * Ruta pedida por un toque en una notificación o un enlace `parcheu://`. `MainActivity` la deja aquí
 * y `ParcheUApp` la consume cuando la sesión está lista (Home en la pila), de modo que un toque con la app
 * cerrada no se salta el login ni el onboarding, pero sí se abre estando en cualquier pantalla.
 */
object PendingDeepLink {
    private val state = MutableStateFlow<AppRoute?>(null)
    val route: StateFlow<AppRoute?> = state.asStateFlow()

    fun offer(route: AppRoute?) {
        if (route != null) state.value = route
    }

    /** Marca la ruta como entregada, solo si sigue siendo la misma (no pisa una más nueva). */
    fun consume(route: AppRoute) {
        state.compareAndSet(route, null)
    }
}
