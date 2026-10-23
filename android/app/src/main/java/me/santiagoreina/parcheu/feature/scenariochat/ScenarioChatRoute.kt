package me.santiagoreina.parcheu.feature.scenariochat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.ScenarioChat

/**
 * Punto de entrada de la ruta [ScenarioChat] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [scenarioChatModule]; AppNavHost.kt no cambia.
 */
@Composable
fun ScenarioChatRoute(route: ScenarioChat, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
