package me.santiagoreina.parcheu.feature.consent

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Consent

/**
 * Punto de entrada de la ruta [Consent] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [consentModule]; AppNavHost.kt no cambia.
 */
@Composable
fun ConsentRoute(route: Consent, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
