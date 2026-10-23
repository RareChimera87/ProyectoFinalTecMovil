package me.santiagoreina.parcheu.feature.parche.fof

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.navigation.AppNavigator
import org.koin.compose.viewmodel.koinViewModel

/** D6/D7 (E6.3): amigos de amigos. Se abre desde el banner o la entrada de D1. */
@Composable
fun FofRoute(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: FofViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.start() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FofEffect.Navigate -> navigator.navigate(effect.route)
            }
        }
    }
    val callbacks =
        remember(viewModel, navigator) {
            FofCallbacks(
                onBack = { navigator.back() },
                onUnlock = viewModel::onUnlock,
                onRetry = viewModel::retry,
                onRequestIntroduction = viewModel::onRequestIntroduction
            )
        }
    FofScreen(state = state, callbacks = callbacks, modifier = modifier)
}
