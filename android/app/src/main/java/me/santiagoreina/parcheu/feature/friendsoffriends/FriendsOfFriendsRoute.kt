package me.santiagoreina.parcheu.feature.friendsoffriends

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.FriendsOfFriends

/**
 * Punto de entrada de la ruta [FriendsOfFriends] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [friendsOfFriendsModule]; AppNavHost.kt no cambia.
 */
@Composable
fun FriendsOfFriendsRoute(route: FriendsOfFriends, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
