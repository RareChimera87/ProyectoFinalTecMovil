package me.santiagoreina.parcheu.feature.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.time.Clock
import me.santiagoreina.parcheu.feature.notifications.requests.ConnectRequestsCallbacks
import me.santiagoreina.parcheu.feature.notifications.requests.ConnectRequestsEffect
import me.santiagoreina.parcheu.feature.notifications.requests.ConnectRequestsSection
import me.santiagoreina.parcheu.feature.notifications.requests.ConnectRequestsViewModel
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Notifications
import org.koin.compose.viewmodel.koinViewModel

/**
 * C2 (E5.4). Campana de C1 → aquí; tocar un aviso abre su ruta (tabla de E5.3: resultado F7/F8,
 * presentación E3, gemelo C4...) y lo marca leído; un aviso bloqueado abre G1. Al volver, C1 recarga la
 * campana.
 */
@Composable
fun NotificationsRoute(
    route: Notifications,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = koinViewModel(),
    /** Sección 'Quieren conectar contigo' (E6.6). Null (pruebas de la bandeja sola) no la dibuja. */
    requestsViewModel: ConnectRequestsViewModel? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is NotificationsEffect.Navigate -> navigator.navigate(effect.route)
            }
        }
    }
    val requestsSection: (@Composable () -> Unit)? =
        requestsViewModel?.let { requests ->
            { RequestsSectionHost(requests, navigator) }
        }
    val callbacks =
        remember(viewModel, navigator) {
            NotificationsCallbacks(
                onBack = { navigator.back() },
                onOpen = viewModel::onOpen,
                onMarkAllRead = viewModel::onMarkAllRead,
                onRetry = viewModel::load,
                onDismissError = viewModel::onDismissError
            )
        }
    NotificationsScreen(
        state = state,
        callbacks = callbacks,
        now = Clock.System.now(),
        modifier = modifier,
        requestsSection = requestsSection
    )
}

/** Conecta la sección con su ViewModel: navega con sus efectos y recarga al volver a C2. */
@Composable
private fun RequestsSectionHost(viewModel: ConnectRequestsViewModel, navigator: AppNavigator) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ConnectRequestsEffect.Navigate -> navigator.navigate(effect.route)
            }
        }
    }
    // Responder una solicitud en F7/F8 la saca de la lista: se vuelve a leer al regresar a C2.
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose {}
    }
    val callbacks =
        remember(viewModel) {
            ConnectRequestsCallbacks(
                onOpenLocked = viewModel::onOpenLocked,
                onOpen = viewModel::onOpen
            )
        }
    ConnectRequestsSection(state, callbacks)
}
