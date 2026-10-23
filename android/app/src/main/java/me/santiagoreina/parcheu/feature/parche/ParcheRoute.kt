package me.santiagoreina.parcheu.feature.parche

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Parche

/**
 * Punto de entrada de la ruta [Parche] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [parcheModule]; AppNavHost.kt no cambia.
 */
@Composable
fun ParcheRoute(route: Parche, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
