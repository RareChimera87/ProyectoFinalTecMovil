package me.santiagoreina.parcheu.feature.splash

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.RouteCatalogList
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Splash

/**
 * Punto de entrada de la ruta [Splash] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [splashModule]; AppNavHost.kt no cambia.
 */
@Composable
fun SplashRoute(route: Splash, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier) {
        RouteCatalogList(navigator = navigator)
    }
}
