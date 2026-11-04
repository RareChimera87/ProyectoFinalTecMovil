package me.santiagoreina.parcheu.feature.report

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.feature.safety.ReportFlowRoute
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Report

/** Punto de entrada de la ruta [Report] (H2 y la confirmación de reporte enviado, E8.1). */
@Composable
fun ReportRoute(route: Report, navigator: AppNavigator, modifier: Modifier = Modifier) {
    ReportFlowRoute(route = route, navigator = navigator, modifier = modifier)
}
