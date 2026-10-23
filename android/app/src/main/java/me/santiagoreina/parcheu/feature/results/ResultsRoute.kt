package me.santiagoreina.parcheu.feature.results

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Chat
import me.santiagoreina.parcheu.navigation.Paywall
import me.santiagoreina.parcheu.navigation.Results
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Origen del paywall cuando se abre desde F7 (analítica y regreso). */
const val RESULTS_PAYWALL_SOURCE = "results"

/** Punto de entrada de la ruta [Results] (F7, F8, I3 y 'esperando', E4.6). */
@Composable
fun ResultsRoute(
    route: Results,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: ResultsViewModel =
        koinViewModel(key = route.simulationId) { parametersOf(route.simulationId) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ResultsEffect.OpenChat -> navigator.navigate(Chat(effect.chatId))
                ResultsEffect.OpenPaywall -> navigator.navigate(Paywall(RESULTS_PAYWALL_SOURCE))
            }
        }
    }

    ResultsScreen(
        state = state,
        actions = ResultsActions(
            onClose = { navigator.back() },
            onRetry = viewModel::onRetry,
            onUnlock = viewModel::onUnlock,
            onSeePlus = viewModel::onSeePlus,
            onConnect = viewModel::onConnect,
            onDecline = viewModel::onDecline,
            onOpenChat = viewModel::onOpenChat,
            onOpenTranscript = viewModel::onOpenTranscript,
            onCloseTranscript = viewModel::onCloseTranscript,
            onDismissNotice = viewModel::onDismissNotice
        ),
        modifier = modifier
    )
}
