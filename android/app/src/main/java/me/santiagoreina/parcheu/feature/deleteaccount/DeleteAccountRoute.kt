package me.santiagoreina.parcheu.feature.deleteaccount

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.data.billing.PlayStoreLinks
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.DeleteAccount
import org.koin.compose.viewmodel.koinViewModel

/**
 * Confirmación de borrado de cuenta (E8.3). Llegan aquí H3 y el bloqueo por edad. Tras borrar, atrás y
 * 'Listo' llevan a A4 sin historial: la cuenta ya no existe.
 */
@Composable
fun DeleteAccountRoute(
    @Suppress("UNUSED_PARAMETER") route: DeleteAccount,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: DeleteAccountViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DeleteAccountEffect.Open -> navigator.navigate(effect.route)
                is DeleteAccountEffect.ReplaceAll -> navigator.replaceAll(effect.route)
                DeleteAccountEffect.OpenPlaySubscriptions ->
                    try {
                        context.startActivity(PlayStoreLinks.manageSubscriptionIntent(context))
                    } catch (_: ActivityNotFoundException) {
                        // Sin Play Store ni navegador: el aviso sigue en pantalla.
                    }
            }
        }
    }
    BackHandler(enabled = state.deleted) { viewModel.onDone() }
    DeleteAccountScreen(
        state = state,
        actions =
        DeleteAccountActions(
            onBack = { navigator.back() },
            onConfirmChange = viewModel::setConfirmed,
            onDelete = viewModel::delete,
            onDownloadFirst = viewModel::onDownloadFirst,
            onManageSubscription = viewModel::onManageSubscription,
            onDone = viewModel::onDone
        ),
        modifier = modifier
    )
}
