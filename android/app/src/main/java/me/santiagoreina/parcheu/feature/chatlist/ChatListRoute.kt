package me.santiagoreina.parcheu.feature.chatlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.contract.ChatType
import me.santiagoreina.parcheu.core.ui.UiState
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.Chat
import me.santiagoreina.parcheu.navigation.ChatList
import me.santiagoreina.parcheu.navigation.ScenarioChat
import org.koin.androidx.compose.koinViewModel

/** Punto de entrada de la ruta [ChatList] (lista de chats, E5.2). */
@Composable
fun ChatListRoute(route: ChatList, navigator: AppNavigator, modifier: Modifier = Modifier) {
    val viewModel: ChatListViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Al volver de F9 se recarga para actualizar no leídos y la última línea.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    ChatListScreen(
        state = state,
        onOpenChat = { chatId ->
            val type = (state as? UiState.Loaded)?.data?.firstOrNull { it.id == chatId }?.type
            navigator.navigate(routeForChat(chatId, type))
        },
        onRetry = viewModel::retry,
        modifier = modifier
    )
}

/**
 * Destino al tocar un chat: los de escenario (E4.8) tienen su propia pantalla (F3, con temporizador y
 * acuerdo); F9 es solo para chats directos. Sin tipo conocido se asume directo.
 */
internal fun routeForChat(chatId: String, type: ChatType?): AppRoute =
    if (type == ChatType.SCENARIO) ScenarioChat(chatId) else Chat(chatId)
