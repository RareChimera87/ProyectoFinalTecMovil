package me.santiagoreina.parcheu.feature.traintwin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.TrainTwin

/**
 * Punto de entrada de la ruta [TrainTwin] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [trainTwinModule]; AppNavHost.kt no cambia.
 */
@Composable
fun TrainTwinRoute(route: TrainTwin, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
