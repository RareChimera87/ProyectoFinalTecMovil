package me.santiagoreina.parcheu.feature.evolution

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Evolution
import org.koin.compose.viewmodel.koinViewModel

/** D9 (E6.5): evolución de la red. Se abre desde la línea de tiempo de D1. */
@Composable
fun EvolutionRoute(
    @Suppress("UNUSED_PARAMETER") route: Evolution,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: EvolutionViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.start() }
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshIfLocked()
        onPauseOrDispose {}
    }
    LaunchedEffect(viewModel) { viewModel.navigation.collect { navigator.navigate(it) } }
    val callbacks =
        remember(viewModel, navigator) {
            EvolutionCallbacks(
                onBack = { navigator.back() },
                onRetry = viewModel::retry,
                onUnlock = viewModel::onUnlock,
                onMonth = viewModel::onMonth
            )
        }
    EvolutionScreen(state = state, callbacks = callbacks, modifier = modifier)
}
