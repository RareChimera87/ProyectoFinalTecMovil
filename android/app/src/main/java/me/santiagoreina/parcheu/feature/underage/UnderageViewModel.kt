package me.santiagoreina.parcheu.feature.underage

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
import me.santiagoreina.parcheu.navigation.Onboarding

sealed interface UnderageEffect {
    /** Abre [route] encima del bloqueo (confirmación de borrado). */
    data class Open(val route: AppRoute) : UnderageEffect

    /** Deja [route] como única pantalla (tras cerrar sesión). */
    data class ReplaceAll(val route: AppRoute) : UnderageEffect
}

/**
 * Bloqueo por edad (E2.6): la cuenta quedó marcada como de menor de edad y la API solo permite verla o
 * borrarla. 'Borrar mi cuenta' abre la confirmación de borrado (`DeleteAccount`, E8.x); 'Cerrar sesión'
 * vuelve a A4.
 */
class UnderageViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _effects = Channel<UnderageEffect>(Channel.BUFFERED)
    val effects: Flow<UnderageEffect> = _effects.receiveAsFlow()

    fun onDeleteAccount() {
        viewModelScope.launch { _effects.send(UnderageEffect.Open(DeleteAccount)) }
    }

    fun onSignOut() {
        viewModelScope.launch {
            auth.signOut()
            _effects.send(UnderageEffect.ReplaceAll(Onboarding(page = LOGIN_PAGE)))
        }
    }
}
