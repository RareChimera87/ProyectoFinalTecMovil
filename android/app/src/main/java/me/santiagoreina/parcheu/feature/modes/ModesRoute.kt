package me.santiagoreina.parcheu.feature.modes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Consent
import me.santiagoreina.parcheu.navigation.Modes
import org.koin.compose.viewmodel.koinViewModel

/**
 * B2 y B3 (E2.5). En el onboarding, al guardar sigue a B4; desde Ajustes ([Modes.fromSettings], H3)
 * vuelve atrás. La flecha de volver solo aparece si hay a dónde volver (B1 o H3).
 */
@Composable
fun ModesRoute(
    route: Modes,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: ModesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ModesEffect.Saved -> if (route.fromSettings) navigator.back() else navigator.navigate(Consent)
            }
        }
    }
    ModesScreen(
        state = state,
        showBack = navigator.canGoBack(),
        onBack = { navigator.back() },
        onToggle = viewModel::onToggle,
        onContinue = viewModel::onContinue,
        onRetry = viewModel::load,
        onDialogConfirmedChange = viewModel::onDialogConfirmedChange,
        onDialogConfirm = viewModel::onDialogConfirm,
        onDialogCancel = viewModel::onDialogCancel,
        modifier = modifier,
    )
}
