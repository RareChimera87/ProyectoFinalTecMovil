package me.santiagoreina.parcheu.feature.matchfound

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.MatchFound

/**
 * Punto de entrada de la ruta [MatchFound] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [matchFoundModule]; AppNavHost.kt no cambia.
 */
@Composable
fun MatchFoundRoute(route: MatchFound, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
