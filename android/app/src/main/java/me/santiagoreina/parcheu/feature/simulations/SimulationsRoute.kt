package me.santiagoreina.parcheu.feature.simulations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.MatchQueue
import me.santiagoreina.parcheu.navigation.Parche
import me.santiagoreina.parcheu.navigation.Results
import me.santiagoreina.parcheu.navigation.Simulation
import me.santiagoreina.parcheu.navigation.Simulations
import org.koin.compose.viewmodel.koinViewModel

/** Punto de entrada de la ruta [Simulations] (F6 y 'Ver todas', E4.5). */
@Composable
fun SimulationsRoute(
    route: Simulations,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: SimulationsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SimulationsScreen(
        state = state,
        onBack = { navigator.back() },
        onOpen = { item ->
            // Lista o en curso/fallida: DONE abre resultados; el resto, la pantalla de simulación (F4/F5).
            navigator.navigate(
                if (item.status == SimulationStatus.DONE) Results(item.id) else Simulation(item.id)
            )
        },
        onPickFromParche = { navigator.navigateToTopLevel(Parche()) },
        onRandomMatch = { navigator.navigate(MatchQueue()) },
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::load,
        modifier = modifier
    )
}
