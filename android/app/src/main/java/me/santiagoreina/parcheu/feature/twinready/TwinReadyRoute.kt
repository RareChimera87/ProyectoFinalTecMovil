package me.santiagoreina.parcheu.feature.twinready

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Home
import me.santiagoreina.parcheu.navigation.Parche
import me.santiagoreina.parcheu.navigation.TwinReady
import org.koin.compose.viewmodel.koinViewModel

/**
 * B5 (E2.5). Cierra el onboarding: la pila queda con Inicio como raíz y, con 'Ver mi parche', la pestaña
 * Tu parche encima (D1).
 */
@Composable
fun TwinReadyRoute(
    @Suppress("UNUSED_PARAMETER") route: TwinReady,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: TwinReadyViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TwinReadyScreen(
        state = state,
        onViewParche = {
            navigator.replaceAll(Home)
            navigator.navigateToTopLevel(Parche())
        },
        onGoHome = { navigator.replaceAll(Home) },
        modifier = modifier,
    )
}
