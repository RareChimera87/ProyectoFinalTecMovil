package me.santiagoreina.parcheu.feature.matchqueue

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.MatchQueue

/**
 * Punto de entrada de la ruta [MatchQueue] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [matchQueueModule]; AppNavHost.kt no cambia.
 */
@Composable
fun MatchQueueRoute(route: MatchQueue, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
