package me.santiagoreina.parcheu.feature.basicprofile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.BasicProfile

/**
 * Punto de entrada de la ruta [BasicProfile] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [basicProfileModule]; AppNavHost.kt no cambia.
 */
@Composable
fun BasicProfileRoute(route: BasicProfile, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
