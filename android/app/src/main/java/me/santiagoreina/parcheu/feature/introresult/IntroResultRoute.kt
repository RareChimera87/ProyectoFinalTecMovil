package me.santiagoreina.parcheu.feature.introresult

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.feature.presentar.IntroResultEntry
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.IntroResult

/** Punto de entrada de la ruta [IntroResult] (E2, E6.2): la pantalla vive en `feature/presentar`. */
@Composable
fun IntroResultRoute(route: IntroResult, navigator: AppNavigator, modifier: Modifier = Modifier) {
    IntroResultEntry(id = route.id, navigator = navigator, modifier = modifier)
}
