package me.santiagoreina.parcheu.feature.traintwin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.feature.safety.reportAiContent
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.TrainTwin
import org.koin.compose.viewmodel.koinViewModel

/**
 * C4 · Entrena a tu gemelo (E9.3). Se abre desde C3 y desde el push `parcheu://train-twin`. 'Reportar'
 * sobre una respuesta del gemelo abre H2 como `AI_CONTENT` (API de E8.7).
 */
@Composable
fun TrainTwinRoute(
    @Suppress("UNUSED_PARAMETER") route: TrainTwin,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: TrainTwinViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.start() }
    val callbacks =
        remember(viewModel, navigator) {
            TrainTwinCallbacks(
                onBack = { navigator.back() },
                onRetry = viewModel::retry,
                onShowAll = viewModel::onShowAll,
                onAccept = viewModel::onAccept,
                onReject = viewModel::onReject,
                onRate = viewModel::onRate,
                onControlAnswer = viewModel::onControlAnswer,
                onReport = { promptId ->
                    parseTurnRef(promptId)?.let {
                        navigator.reportAiContent(it.simulationId, it.turnIndex)
                    }
                }
            )
        }
    TrainTwinScreen(state = state, callbacks = callbacks, modifier = modifier)
}
