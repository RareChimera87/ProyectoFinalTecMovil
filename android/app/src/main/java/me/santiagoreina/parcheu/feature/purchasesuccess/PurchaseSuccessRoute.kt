package me.santiagoreina.parcheu.feature.purchasesuccess

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.feature.plus.PurchaseSuccessEntry
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.PurchaseSuccess

/** Punto de entrada de la ruta [PurchaseSuccess] (G3). La pantalla vive en `feature/plus` (E7.3). */
@Composable
fun PurchaseSuccessRoute(
    route: PurchaseSuccess,
    navigator: AppNavigator,
    modifier: Modifier = Modifier
) {
    PurchaseSuccessEntry(route = route, navigator = navigator, modifier = modifier)
}
