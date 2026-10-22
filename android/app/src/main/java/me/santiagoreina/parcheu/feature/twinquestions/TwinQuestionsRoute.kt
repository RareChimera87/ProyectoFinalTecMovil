package me.santiagoreina.parcheu.feature.twinquestions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.core.ui.StubScreen
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.TwinQuestions

/**
 * Punto de entrada de la ruta [TwinQuestions] (stub de E0.7). La feature dueña reemplaza solo este archivo y
 * [twinQuestionsModule]; AppNavHost.kt no cambia.
 */
@Composable
fun TwinQuestionsRoute(
    route: TwinQuestions,
    navigator: AppNavigator,
    modifier: Modifier = Modifier
) {
    StubScreen(route = route, navigator = navigator, modifier = modifier)
}
