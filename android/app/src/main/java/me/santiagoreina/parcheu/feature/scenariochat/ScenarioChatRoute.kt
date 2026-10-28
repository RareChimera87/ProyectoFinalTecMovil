package me.santiagoreina.parcheu.feature.scenariochat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.feature.conocer.scenario.ScenarioRoute
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.ScenarioChat

/** Punto de entrada de la ruta [ScenarioChat] (F3). La pantalla vive en `feature/conocer/scenario` (E4.8). */
@Composable
fun ScenarioChatRoute(route: ScenarioChat, navigator: AppNavigator, modifier: Modifier = Modifier) {
    ScenarioRoute(route, navigator, modifier)
}
