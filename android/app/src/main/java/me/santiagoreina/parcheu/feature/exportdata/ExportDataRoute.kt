package me.santiagoreina.parcheu.feature.exportdata

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.ExportData

/**
 * Punto de entrada de la ruta [ExportData] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [exportDataModule]; AppNavHost.kt no cambia.
 */
@Composable
fun ExportDataRoute(route: ExportData, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
