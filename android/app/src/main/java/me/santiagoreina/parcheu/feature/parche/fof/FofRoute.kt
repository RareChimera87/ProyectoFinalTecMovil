package me.santiagoreina.parcheu.feature.parche.fof

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.feature.safety.SafetyEntryHost
import me.santiagoreina.parcheu.feature.safety.SafetyTarget
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
    var safetyTarget by remember { mutableStateOf<SafetyTarget?>(null) }
    LaunchedEffect(viewModel) { viewModel.start() }
    // Volver de G1 con ParcheU+ recién comprado: D7 se recarga y pasa a D6.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshIfLocked()
        onPauseOrDispose {}
    }
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
                onRequestIntroduction = viewModel::onRequestIntroduction,
                onReportOrBlock = { anonId, name ->
                    safetyTarget = SafetyTarget(id = anonId, name = name, anonymous = true)
                }
            )
        }
    FofScreen(state = state, callbacks = callbacks, modifier = modifier)
    // E8.7: H1 sobre D6 para reportar o bloquear a un amigo de amigos; al bloquear se recarga la lista.
    SafetyEntryHost(
        target = safetyTarget,
        navigator = navigator,
        onClear = { safetyTarget = null },
        onBlocked = viewModel::retry
    )
}
