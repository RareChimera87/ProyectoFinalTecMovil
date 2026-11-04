package me.santiagoreina.parcheu.feature.parche.communities

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Parche
import org.koin.compose.viewmodel.koinViewModel

/** D8 (E6.4): comunidades. Se abre desde el filtro "Comunidad" de D1 (ParcheU+). */
@Composable
fun CommunitiesScreenRoute(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: CommunitiesViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.start() }
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshIfLocked()
        onPauseOrDispose {}
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CommunitiesEffect.Navigate ->
                    if (effect.replaceCommunities) {
                        navigator.navigateAsNew(effect.route, Parche::class)
                    } else {
                        navigator.navigate(effect.route)
                    }
            }
        }
    }
    val callbacks =
        remember(viewModel, navigator) {
            CommunitiesCallbacks(
                onBack = { navigator.back() },
                onUnlock = viewModel::onUnlock,
                onRetry = viewModel::retry,
                onViewInGraph = viewModel::onViewInGraph
            )
        }
    CommunitiesScreen(state = state, callbacks = callbacks, modifier = modifier)
}
