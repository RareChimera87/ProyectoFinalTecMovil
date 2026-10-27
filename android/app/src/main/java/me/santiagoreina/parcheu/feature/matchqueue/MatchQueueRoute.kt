package me.santiagoreina.parcheu.feature.matchqueue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.feature.conocer.match.MatchQueueEffect
import me.santiagoreina.parcheu.feature.conocer.match.MatchQueueScreen
import me.santiagoreina.parcheu.feature.conocer.match.MatchQueueViewModel
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Home
import me.santiagoreina.parcheu.navigation.MatchFound
import me.santiagoreina.parcheu.navigation.MatchQueue
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** F1 (E4.4): ver `feature/conocer/match`. Cancelar o cerrar vuelve a donde estaba, o a Inicio (C1). */
@Composable
fun MatchQueueRoute(
    route: MatchQueue,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: MatchQueueViewModel = koinViewModel { parametersOf(route.mode) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MatchQueueEffect.OpenMatch -> navigator.navigate(MatchFound(effect.matchId))
                MatchQueueEffect.Close ->
                    if (navigator.canGoBack()) {
                        navigator.back()
                    } else {
                        navigator.navigateToTopLevel(
                            Home
                        )
                    }
                is MatchQueueEffect.OpenLimit ->
                    navigator.navigateReplacing(effect.route, MatchQueue::class)
            }
        }
    }
    MatchQueueScreen(
        state = state,
        onModeSelected = viewModel::onModeSelected,
        onCancel = viewModel::onCancel,
        onRetry = viewModel::onRetry,
        onOpenMatch = viewModel::onOpenMatchAgain,
        modifier = modifier
    )
}
