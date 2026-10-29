package me.santiagoreina.parcheu.feature.suspended

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.data.auth.AuthRepository
import me.santiagoreina.parcheu.data.auth.LOGIN_PAGE
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.DeleteAccount
import me.santiagoreina.parcheu.navigation.ExportData
import me.santiagoreina.parcheu.navigation.Onboarding

sealed interface SuspendedEffect {
    /** Abre [route] encima del bloqueo ('Descargar mis datos' o la confirmación de borrado). */
    data class Open(val route: AppRoute) : SuspendedEffect

    /** Deja [route] como única pantalla (tras cerrar sesión). */
    data class ReplaceAll(val route: AppRoute) : SuspendedEffect
}

/**
 * Bloqueo por suspensión: la API responde 403 SUSPENDED salvo en las rutas exentas (`SUSPENSION_EXEMPT`
 * en `TermsGate.kt`, más `DELETE /v1/me` y `GET /v1/me/export`). La persona conserva la sesión para
 * ejercer sus derechos (Ley 1581): descargar sus datos, borrar la cuenta o cerrar sesión.
 */
class SuspendedViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _effects = Channel<SuspendedEffect>(Channel.BUFFERED)
    val effects: Flow<SuspendedEffect> = _effects.receiveAsFlow()

    fun onExportData() {
        viewModelScope.launch { _effects.send(SuspendedEffect.Open(ExportData)) }
    }

    fun onDeleteAccount() {
        viewModelScope.launch { _effects.send(SuspendedEffect.Open(DeleteAccount)) }
    }

    fun onSignOut() {
        viewModelScope.launch {
            auth.signOut()
            _effects.send(SuspendedEffect.ReplaceAll(Onboarding(page = LOGIN_PAGE)))
        }
    }
}
