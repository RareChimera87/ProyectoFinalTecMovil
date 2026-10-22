package me.santiagoreina.parcheu.feature.underage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Underage
import org.koin.compose.viewmodel.koinViewModel

/** Bloqueo por edad (E2.6). Llegan aquí B0 (403 UNDERAGE), el Splash y A4 cuando la sesión responde UNDERAGE. */
@Composable
fun UnderageRoute(
    @Suppress("UNUSED_PARAMETER") route: Underage,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: UnderageViewModel = koinViewModel(),
) {
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UnderageEffect.Open -> navigator.navigate(effect.route)
                is UnderageEffect.ReplaceAll -> navigator.replaceAll(effect.route)
            }
        }
    }
    UnderageScreen(onDeleteAccount = viewModel::onDeleteAccount, onSignOut = viewModel::onSignOut, modifier = modifier)
}
