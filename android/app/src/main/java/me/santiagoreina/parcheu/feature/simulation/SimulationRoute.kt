package me.santiagoreina.parcheu.feature.simulation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Home
import me.santiagoreina.parcheu.navigation.Results
import me.santiagoreina.parcheu.navigation.Simulation
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Punto de entrada de la ruta [Simulation] (F4/F5, E4.5). Al terminar (DONE) o al reintentar, esta pantalla
 * se reemplaza (se saca de la pila y se abre la siguiente) para que "atrás" desde F7 no vuelva a F4.
 */
@Composable
fun SimulationRoute(
    route: Simulation,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: SimulationViewModel = koinViewModel(key = route.id) { parametersOf(route.id) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is SimulationEffect.OpenPaywall) {
                navigator.navigate(effect.route)
                return@collect
            }
            navigator.back()
            navigator.navigate(
                when (effect) {
                    is SimulationEffect.OpenResults -> Results(effect.simulationId)
                    is SimulationEffect.OpenSimulation -> Simulation(effect.simulationId)
                    is SimulationEffect.OpenPaywall -> error("manejado arriba")
                }
            )
        }
    }

    SimulationScreen(
        state = state,
        onClose = { navigator.back() },
        onReload = viewModel::onReload,
        onRetry = viewModel::onRetry,
        onHome = { navigator.navigateToTopLevel(Home) },
        modifier = modifier
    )
}
