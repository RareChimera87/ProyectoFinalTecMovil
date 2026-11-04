package me.santiagoreina.parcheu.feature.paywall

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.data.billing.BillingRepository
import me.santiagoreina.parcheu.feature.plus.PaywallEntry
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Paywall
import org.koin.compose.koinInject

/**
 * Punto de entrada de la ruta [Paywall] (G1, I4 y la hoja G2). La pantalla vive en `feature/plus` (E7.3);
 * este archivo solo la conecta con AppNavHost.kt, que no cambia.
 */
@Composable
fun PaywallRoute(route: Paywall, navigator: AppNavigator, modifier: Modifier = Modifier) {
    val billing = koinInject<BillingRepository>()
    val entitlement by billing.entitlement.collectAsStateWithLifecycle()
    PaywallEntry(
        route = route,
        navigator = navigator,
        modifier = modifier,
        isPlus =
        entitlement?.plus == true
    )
}
