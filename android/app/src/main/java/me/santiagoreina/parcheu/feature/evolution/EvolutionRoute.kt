package me.santiagoreina.parcheu.feature.evolution

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Evolution

/**
 * Punto de entrada de la ruta [Evolution] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [evolutionModule]; AppNavHost.kt no cambia.
 */
@Composable
fun EvolutionRoute(route: Evolution, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
