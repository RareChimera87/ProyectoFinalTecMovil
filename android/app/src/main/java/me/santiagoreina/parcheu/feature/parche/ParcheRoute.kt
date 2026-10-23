package me.santiagoreina.parcheu.feature.parche

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.navigation.AddFriends
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Communities
import me.santiagoreina.parcheu.navigation.Evolution
import me.santiagoreina.parcheu.navigation.Introduce
import me.santiagoreina.parcheu.navigation.MatchQueue
import me.santiagoreina.parcheu.navigation.Parche
import me.santiagoreina.parcheu.navigation.Paywall
import org.koin.compose.viewmodel.koinViewModel

/** Origen del paywall cuando se abre desde D1 (analítica de G1). */
const val PAYWALL_SOURCE_PARCHE = "parche"

/**
 * D1-D5 (E3.4). [Parche.community] llega de D8 y filtra el grafo; el candado y las funciones de ParcheU+
 * (comunidades, evolución) llevan al paywall G1 cuando la persona no tiene el plan.
 */
@Composable
fun ParcheRoute(
    route: Parche,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: ParcheViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(route.community) { viewModel.start(route.community) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ParcheEffect.Navigate -> navigator.navigate(effect.route)
            }
        }
    }
    val plus = (state as? ParcheUiState.Loaded)?.content?.plus == true
    val callbacks =
        remember(viewModel, navigator, plus) {
            ParcheCallbacks(
                onMode = viewModel::onModeFilter,
                onAffinity = viewModel::onAffinityFilter,
                onCommunity = {
                    val loaded = viewModel.state.value as? ParcheUiState.Loaded
                    when {
                        loaded?.filters?.communityId != null -> viewModel.onClearCommunity()
                        plus -> navigator.navigate(Communities)
                        else -> navigator.navigate(Paywall(PAYWALL_SOURCE_PARCHE))
                    }
                },
                onNodeClick = viewModel::onNodeClick,
                onDismissSheet = viewModel::onDismissSheet,
                onSimulateAgain = viewModel::onSimulateAgain,
                onChat = viewModel::onChat,
                onViewResult = viewModel::onViewResult,
                onRetry = viewModel::retry,
                onTimeline = {
                    navigator.navigate(if (plus) Evolution else Paywall(PAYWALL_SOURCE_PARCHE))
                },
                onAddFriends = { navigator.navigate(AddFriends()) },
                onRandomMatch = { navigator.navigate(MatchQueue()) },
                onUnlock = { navigator.navigate(Paywall(PAYWALL_SOURCE_PARCHE)) },
                onIntroduce = { navigator.navigate(Introduce) }
            )
        }
    ParcheScreen(state = state, callbacks = callbacks, modifier = modifier)
}
