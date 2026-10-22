package me.santiagoreina.parcheu.feature.blockedusers

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.BlockedUsers

/**
 * Punto de entrada de la ruta [BlockedUsers] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [blockedUsersModule]; AppNavHost.kt no cambia.
 */
@Composable
fun BlockedUsersRoute(route: BlockedUsers, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
