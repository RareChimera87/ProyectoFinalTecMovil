package me.santiagoreina.parcheu.feature.splash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Splash
import org.koin.compose.viewmodel.koinViewModel

/** A1 · Splash (E2.2): decide el destino según la sesión y deja solo esa pantalla en la pila. */
@Composable
fun SplashRoute(
    @Suppress("UNUSED_PARAMETER") route: Splash,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: SplashViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.destination.collect { navigator.replaceAll(it) }
    }
    SplashScreen(state = state, onRetry = viewModel::onRetry, modifier = modifier)
}
