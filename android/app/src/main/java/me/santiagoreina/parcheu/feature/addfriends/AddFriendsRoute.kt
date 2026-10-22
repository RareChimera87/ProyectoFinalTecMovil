package me.santiagoreina.parcheu.feature.addfriends

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AddFriends
import me.santiagoreina.parcheu.navigation.AppNavigator

/**
 * Punto de entrada de la ruta [AddFriends] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [addFriendsModule]; AppNavHost.kt no cambia.
 */
@Composable
fun AddFriendsRoute(route: AddFriends, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
