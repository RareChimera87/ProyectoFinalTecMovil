package me.santiagoreina.parcheu.feature.deleteaccount

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.DeleteAccount

/**
 * Punto de entrada de la ruta [DeleteAccount] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [deleteAccountModule]; AppNavHost.kt no cambia.
 */
@Composable
fun DeleteAccountRoute(route: DeleteAccount, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
