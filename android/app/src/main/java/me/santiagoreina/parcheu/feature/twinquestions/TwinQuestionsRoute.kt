package me.santiagoreina.parcheu.feature.twinquestions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Modes
import me.santiagoreina.parcheu.navigation.TwinQuestions
import org.koin.androidx.compose.koinViewModel

/** Punto de entrada de la ruta [TwinQuestions] (B1). Al terminar las 12 preguntas sigue a B2. */
@Composable
fun TwinQuestionsRoute(
    route: TwinQuestions,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: TwinQuestionsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, navigator) {
        viewModel.events.collect { event ->
            when (event) {
                TwinQuestionsEvent.Finished -> navigator.continueToModes()
            }
        }
    }
    TwinQuestionsContent(
        state = state,
        onAnswer = viewModel::answer,
        onClose = { navigator.back() },
        onRetry = viewModel::retry,
        modifier = modifier,
        showClose = navigator.canGoBack()
    )
}

/**
 * Pasa de B1 a B2 sacando B1 de la pila. B1 no es reanudable tras enviar (el progreso ya se borró), así
 * que dejarla debajo de B2 la mostraría trabada en "Enviando…" al volver atrás.
 */
internal fun AppNavigator.continueToModes() {
    back()
    navigate(Modes())
}
