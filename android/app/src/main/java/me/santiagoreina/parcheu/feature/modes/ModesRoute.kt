package me.santiagoreina.parcheu.feature.modes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Modes

/**
 * Punto de entrada de la ruta [Modes] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [modesModule]; AppNavHost.kt no cambia.
 */
@Composable
fun ModesRoute(route: Modes, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
