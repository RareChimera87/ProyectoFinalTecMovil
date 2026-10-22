package me.santiagoreina.parcheu.feature.matchfound

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.feature.conocer.match.MatchFoundEffect
import me.santiagoreina.parcheu.feature.conocer.match.MatchFoundScreen
import me.santiagoreina.parcheu.feature.conocer.match.MatchFoundViewModel
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Home
import me.santiagoreina.parcheu.navigation.MatchFound
import me.santiagoreina.parcheu.navigation.MatchQueue
import me.santiagoreina.parcheu.navigation.ScenarioChat
import me.santiagoreina.parcheu.navigation.Simulation
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * F2 (E4.4): ver `feature/conocer/match`. Las rutas F3 y F4 reciben el id del match porque el contrato
 * (`MatchDto`) no devuelve el id del chat ni de la simulación; el backend los resuelve por match.
 */
@Composable
fun MatchFoundRoute(
    route: MatchFound,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: MatchFoundViewModel = koinViewModel { parametersOf(route.matchId) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MatchFoundEffect.OpenScenario -> navigator.navigate(ScenarioChat(effect.matchId))
                is MatchFoundEffect.OpenSimulation -> navigator.navigate(Simulation(effect.matchId))
                MatchFoundEffect.SearchAgain -> navigator.navigate(MatchQueue())
                MatchFoundEffect.Close ->
                    if (navigator.canGoBack()) {
                        navigator.back()
                    } else {
                        navigator.navigateToTopLevel(
                            Home
                        )
                    }
            }
        }
    }
    MatchFoundScreen(
        state = state,
        onSelect = viewModel::onSelect,
        onStart = viewModel::onStart,
        onRetry = viewModel::load,
        onSearchAgain = viewModel::onSearchAgain,
        onClose = viewModel::onClose,
        modifier = modifier
    )
}
