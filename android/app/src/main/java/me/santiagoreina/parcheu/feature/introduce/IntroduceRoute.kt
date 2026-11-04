package me.santiagoreina.parcheu.feature.introduce

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.feature.presentar.IntroduceEntry
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Introduce

/** Punto de entrada de la ruta [Introduce] (E1, E6.2): la pantalla vive en `feature/presentar`. */
@Composable
fun IntroduceRoute(route: Introduce, navigator: AppNavigator, modifier: Modifier = Modifier) {
    IntroduceEntry(navigator = navigator, modifier = modifier)
}
