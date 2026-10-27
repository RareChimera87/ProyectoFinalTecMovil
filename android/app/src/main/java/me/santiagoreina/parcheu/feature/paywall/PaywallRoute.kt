package me.santiagoreina.parcheu.feature.paywall

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.feature.plus.PaywallEntry
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Paywall

/**
 * Punto de entrada de la ruta [Paywall] (G1, I4 y la hoja G2). La pantalla vive en `feature/plus` (E7.3);
 * este archivo solo la conecta con AppNavHost.kt, que no cambia.
 */
@Composable
fun PaywallRoute(route: Paywall, navigator: AppNavigator, modifier: Modifier = Modifier) {
    PaywallEntry(route = route, navigator = navigator, modifier = modifier)
}
