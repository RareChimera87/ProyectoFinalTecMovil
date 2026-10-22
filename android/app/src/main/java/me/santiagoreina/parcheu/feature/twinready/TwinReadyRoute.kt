package me.santiagoreina.parcheu.feature.twinready

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.TwinReady

/**
 * Punto de entrada de la ruta [TwinReady] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [twinReadyModule]; AppNavHost.kt no cambia.
 */
@Composable
fun TwinReadyRoute(route: TwinReady, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
