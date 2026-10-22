package me.santiagoreina.parcheu.feature.privacysettings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.PrivacySettings

/**
 * Punto de entrada de la ruta [PrivacySettings] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [privacySettingsModule]; AppNavHost.kt no cambia.
 */
@Composable
fun PrivacySettingsRoute(route: PrivacySettings, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
