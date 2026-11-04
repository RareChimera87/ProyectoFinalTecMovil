package me.santiagoreina.parcheu.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Profile
import org.koin.compose.viewmodel.koinViewModel

/**
 * C3 · Perfil y gemelo (E9.1). Recarga al entrar (al volver de B0 en edición los datos cambian).
 * Engranaje → H3, 'Editar perfil' → B0 (edición), 'Entrenar a mi gemelo' → C4, suscripción → G4 y
 * 'Agregar amigos' → C5.
 */
@Composable
fun ProfileRoute(
    route: Profile,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.load() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ProfileEffect.Navigate -> navigator.navigate(effect.route)
            }
        }
    }
    val callbacks =
        remember(viewModel) {
            ProfileCallbacks(
                onSettings = viewModel::onSettings,
                onEditProfile = viewModel::onEditProfile,
                onTrainTwin = viewModel::onTrainTwin,
                onSubscription = viewModel::onSubscription,
                onAddFriends = viewModel::onAddFriends,
                onRetry = viewModel::load
            )
        }
    ProfileScreen(state = state, callbacks = callbacks, modifier = modifier)
}
