package me.santiagoreina.parcheu.feature.chatlist

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.ChatList

/**
 * Punto de entrada de la ruta [ChatList] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [chatListModule]; AppNavHost.kt no cambia.
 */
@Composable
fun ChatListRoute(route: ChatList, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
