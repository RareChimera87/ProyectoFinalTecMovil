package me.santiagoreina.parcheu.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Chat

/**
 * Punto de entrada de la ruta [Chat] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [chatModule]; AppNavHost.kt no cambia.
 */
@Composable
fun ChatRoute(route: Chat, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
