package me.santiagoreina.parcheu.feature.simulation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Simulation

/**
 * Punto de entrada de la ruta [Simulation] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [simulationModule]; AppNavHost.kt no cambia.
 */
@Composable
fun SimulationRoute(route: Simulation, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
