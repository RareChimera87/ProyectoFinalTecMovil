package me.santiagoreina.parcheu.feature.chatlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Chat
import me.santiagoreina.parcheu.navigation.ChatList
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
        onOpenChat = { navigator.navigate(Chat(it)) },
        onRetry = viewModel::retry,
        modifier = modifier
    )
}
