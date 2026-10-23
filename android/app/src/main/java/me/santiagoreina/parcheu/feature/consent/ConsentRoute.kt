package me.santiagoreina.parcheu.feature.consent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Consent
import me.santiagoreina.parcheu.navigation.TwinReady
import org.koin.compose.viewmodel.koinViewModel

/** B4 (E2.5). 'Acepto' y 'Ahora no' llevan a B5; atrás vuelve a B2. */
@Composable
fun ConsentRoute(
    @Suppress("UNUSED_PARAMETER") route: Consent,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: ConsentViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ConsentEffect.Done -> navigator.navigate(TwinReady)
            }
        }
    }
    ConsentScreen(
        state = state,
        showBack = navigator.canGoBack(),
        onBack = { navigator.back() },
        onAccept = viewModel::onAccept,
        onNotNow = viewModel::onNotNow,
        modifier = modifier
    )
}
