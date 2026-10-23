package me.santiagoreina.parcheu.feature.introduce

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Introduce

/**
 * Punto de entrada de la ruta [Introduce] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [introduceModule]; AppNavHost.kt no cambia.
 */
@Composable
fun IntroduceRoute(route: Introduce, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
