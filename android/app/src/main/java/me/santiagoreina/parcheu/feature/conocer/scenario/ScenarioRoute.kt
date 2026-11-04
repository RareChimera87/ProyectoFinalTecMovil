package me.santiagoreina.parcheu.feature.conocer.scenario

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import me.santiagoreina.parcheu.feature.chat.ChatContent
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Results
import me.santiagoreina.parcheu.navigation.ScenarioChat
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * F3 · Modo escenario (E4.8): el chat de F9 con la tarjeta del escenario fijada arriba y la zona de acuerdo.
 * Con el resultado listo reemplaza F3 por F7/F8 (la persona no vuelve a un chat cerrado).
 */
@Composable
fun ScenarioRoute(route: ScenarioChat, navigator: AppNavigator, modifier: Modifier = Modifier) {
    val viewModel: ScenarioViewModel =
        koinViewModel(key = route.chatId) { parametersOf(route.chatId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ScenarioEffect.OpenResults ->
                    navigator.navigateReplacing(Results(effect.simulationId), ScenarioChat::class)
            }
        }
    }
    // Temporizador y sondeo viven con la pantalla (se detienen al salir de F3).
    LaunchedEffect(viewModel) {
        var elapsed = 0L
        while (true) {
            delay(TICK_MS)
            viewModel.onTick()
            elapsed += TICK_MS
            if (elapsed >= ScenarioViewModel.POLL_INTERVAL_MS) {
                elapsed = 0
                viewModel.poll()
            }
        }
    }
    ChatContent(
        chatId = route.chatId,
        navigator = navigator,
        modifier = modifier,
        pinned = { ScenarioPinnedCard(state) },
        footer = {
            ScenarioAgreementBar(
                state,
                onAgree = viewModel::onAgree,
                onOpenResults = viewModel::onOpenResults
            )
        }
    )
}

private const val TICK_MS = 1_000L
