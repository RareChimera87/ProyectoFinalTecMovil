package me.santiagoreina.parcheu.feature.communities

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.feature.parche.communities.CommunitiesScreenRoute
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Communities

/**
 * Punto de entrada de la ruta [Communities] (D8). La pantalla vive en `feature/parche/communities`
 * (E6.4); AppNavHost.kt no cambia.
 */
@Composable
fun CommunitiesRoute(
    @Suppress("UNUSED_PARAMETER") route: Communities,
    navigator: AppNavigator,
    modifier: Modifier = Modifier
) {
    CommunitiesScreenRoute(navigator = navigator, modifier = modifier)
}
