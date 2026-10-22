package me.santiagoreina.parcheu.feature.introresult

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.IntroResult

/**
 * Punto de entrada de la ruta [IntroResult] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [introResultModule]; AppNavHost.kt no cambia.
 */
@Composable
fun IntroResultRoute(route: IntroResult, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
