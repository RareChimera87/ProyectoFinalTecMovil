package me.santiagoreina.parcheu.feature.managesubscription

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.feature.plus.ManageSubscriptionEntry
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.ManageSubscription

/** Punto de entrada de la ruta [ManageSubscription] (G4). La pantalla vive en `feature/plus` (E7.3). */
@Composable
fun ManageSubscriptionRoute(
    route: ManageSubscription,
    navigator: AppNavigator,
    modifier: Modifier = Modifier
) {
    ManageSubscriptionEntry(navigator = navigator, modifier = modifier)
}
