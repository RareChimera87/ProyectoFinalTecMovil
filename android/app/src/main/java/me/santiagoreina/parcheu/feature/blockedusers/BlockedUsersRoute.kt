package me.santiagoreina.parcheu.feature.blockedusers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.BlockedUsers
import org.koin.compose.viewmodel.koinViewModel

/** Punto de entrada de la ruta [BlockedUsers]: 'Personas bloqueadas' (desde H3, E8.7). */
@Composable
fun BlockedUsersRoute(
    route: BlockedUsers,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: BlockedUsersViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BlockedUsersScreen(
        state = state,
        actions = BlockedUsersActions(
            onBack = { navigator.back() },
            onUnblock = viewModel::unblock,
            onRetry = viewModel::retry
        ),
        modifier = modifier
    )
}
