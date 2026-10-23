package me.santiagoreina.parcheu.feature.friendsoffriends

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.feature.parche.fof.FofRoute
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.FriendsOfFriends

/**
 * Punto de entrada de la ruta [FriendsOfFriends] (D6/D7). La pantalla vive en `feature/parche/fof`
 * (E6.3); AppNavHost.kt no cambia.
 */
@Composable
fun FriendsOfFriendsRoute(
    route: FriendsOfFriends,
    navigator: AppNavigator,
    modifier: Modifier = Modifier
) {
    FofRoute(navigator = navigator, modifier = modifier)
}
