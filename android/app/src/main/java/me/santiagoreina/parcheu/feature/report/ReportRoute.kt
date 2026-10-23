package me.santiagoreina.parcheu.feature.report

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Report

/**
 * Punto de entrada de la ruta [Report] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [reportModule]; AppNavHost.kt no cambia.
 */
@Composable
fun ReportRoute(route: Report, navigator: AppNavigator, modifier: Modifier = Modifier) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
