package me.santiagoreina.parcheu.feature.results

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Results

/**
 * Punto de entrada de la ruta [Results] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [resultsModule]; AppNavHost.kt no cambia.
 */
@Composable
fun ResultsRoute(route: Results, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
