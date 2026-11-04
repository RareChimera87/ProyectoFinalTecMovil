package me.santiagoreina.parcheu.feature.suspended

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Suspended
import org.koin.compose.viewmodel.koinViewModel

/** Bloqueo por suspensión. Llega aquí `GlobalErrorHandler` ante un 403 SUSPENDED (arquitectura.md §2.3). */
@Composable
fun SuspendedRoute(
    @Suppress("UNUSED_PARAMETER") route: Suspended,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: SuspendedViewModel = koinViewModel()
) {
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SuspendedEffect.Open -> navigator.navigate(effect.route)
                is SuspendedEffect.ReplaceAll -> navigator.replaceAll(effect.route)
            }
        }
    }
    val signingOut by viewModel.signingOut.collectAsStateWithLifecycle()
    SuspendedScreen(
        onExportData = viewModel::onExportData,
        onDeleteAccount = viewModel::onDeleteAccount,
        onSignOut = viewModel::onSignOut,
        signingOut = signingOut,
        modifier = modifier
    )
}
