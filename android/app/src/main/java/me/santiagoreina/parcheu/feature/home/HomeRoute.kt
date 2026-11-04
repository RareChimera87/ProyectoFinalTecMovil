package me.santiagoreina.parcheu.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.time.Clock
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Home
import me.santiagoreina.parcheu.navigation.MatchQueue
import me.santiagoreina.parcheu.navigation.Notifications
import me.santiagoreina.parcheu.navigation.Parche
import me.santiagoreina.parcheu.navigation.Paywall
import me.santiagoreina.parcheu.navigation.Profile
import me.santiagoreina.parcheu.navigation.Results
import me.santiagoreina.parcheu.navigation.Simulation
import me.santiagoreina.parcheu.navigation.Simulations
import org.koin.compose.viewmodel.koinViewModel

/**
 * C1 / I1 (E4.7). Recarga `GET /v1/home` cada vez que se entra a Inicio (al volver de F4, F7 o C2 el
 * cupo, la simulación en curso y las no leídas cambian). Campana → C2, avatar → C3, en curso → F4,
 * resultado listo → F7/F8, "Simular" → F4, "Buscar" → F1 y el chip ParcheU+ → G1.
 */
@Composable
fun HomeRoute(
    route: Home,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.load() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.Navigate -> navigator.navigate(effect.route)
            }
        }
    }
    val callbacks =
        remember(viewModel, navigator) {
            HomeCallbacks(
                onBell = { navigator.navigate(Notifications) },
                onAvatar = { navigator.navigateToTopLevel(Profile) },
                onSeeAll = { navigator.navigate(Simulations) },
                onOpenSimulation = { item ->
                    navigator.navigate(
                        if (item.status ==
                            SimulationStatus.DONE
                        ) {
                            Results(item.id)
                        } else {
                            Simulation(item.id)
                        }
                    )
                },
                onSimulate = viewModel::onSimulate,
                onRandomMatch = { navigator.navigate(MatchQueue()) },
                onPickFromParche = { navigator.navigateToTopLevel(Parche()) },
                onPlus = { navigator.navigate(Paywall(PAYWALL_SOURCE_HOME)) },
                onRetry = viewModel::load,
                onDismissError = viewModel::onDismissError
            )
        }
    HomeScreen(state = state, callbacks = callbacks, now = Clock.System.now(), modifier = modifier)
}
