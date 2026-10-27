package me.santiagoreina.parcheu.feature.plus

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.R
import me.santiagoreina.parcheu.data.billing.PlayStoreLinks
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.FriendsOfFriends
import me.santiagoreina.parcheu.navigation.Home
import me.santiagoreina.parcheu.navigation.ManageSubscription
import me.santiagoreina.parcheu.navigation.Paywall
import me.santiagoreina.parcheu.navigation.PurchaseSuccess
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Origen del paywall cuando se abre desde la hoja de límite (G2) o desde G4. */
const val PAYWALL_SOURCE_LIMIT = "limit"
const val PAYWALL_SOURCE_MANAGE = "manage"

/**
 * Ruta [Paywall]: G1 o, si el `source` es un límite (`PlusSources.limit`), la hoja G2 de ese cupo.
 * "Ver ParcheU+" en G2 reemplaza la hoja por G1.
 */
@Composable
fun PaywallEntry(
    route: Paywall,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    paywall: @Composable (AppNavigator, Modifier) -> Unit = { nav, mod -> PaywallRoute(nav, mod) }
) {
    when (val entry = PlusSources.parse(route.source)) {
        is PlusEntry.Limit ->
            LimitSheet(
                kind = entry.kind,
                resetsAt = entry.resetsAt,
                onSeePlus = {
                    navigator.navigateReplacing(Paywall(PAYWALL_SOURCE_LIMIT), Paywall::class)
                },
                onDismiss = { navigator.back() },
                modifier = modifier
            )
        is PlusEntry.Paywall -> paywall(navigator, modifier)
    }
}

/** G1 con su ViewModel; interna para que las pruebas la usen con un `BillingRepository` falso. */
@Composable
internal fun PaywallRoute(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: PaywallViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PaywallEffect.OpenSuccess ->
                    navigator.navigateReplacing(PurchaseSuccess(effect.plan), Paywall::class)
                PaywallEffect.OpenManage ->
                    navigator.navigateReplacing(ManageSubscription, Paywall::class)
            }
        }
    }
    val actions =
        remember(viewModel, navigator, context) {
            PaywallActions(
                onClose = { navigator.back() },
                onRestore = viewModel::onRestore,
                onSelect = viewModel::onSelect,
                onSubscribe = { context.findActivity()?.let(viewModel::onSubscribe) },
                onRetry = viewModel::loadPlans,
                onDismissMessage = viewModel::onDismissMessage
            )
        }
    PaywallScreen(state = state, actions = actions, modifier = modifier)
}

/** Ruta [PurchaseSuccess] (G3). */
@Composable
fun PurchaseSuccessEntry(
    route: PurchaseSuccess,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: PurchaseSuccessViewModel =
        koinViewModel(key = "purchase-success-${route.plan}") { parametersOf(route.plan) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PurchaseSuccessScreen(
        state = state,
        actions =
        PurchaseSuccessActions(
            onExplore = { navigator.navigateReplacing(FriendsOfFriends, PurchaseSuccess::class) },
            onHome = { navigator.navigateToTopLevel(Home) }
        ),
        modifier = modifier
    )
}

/** Ruta [ManageSubscription] (G4). Recarga el derecho cada vez que se entra. */
@Composable
fun ManageSubscriptionEntry(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: ManageSubscriptionViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCancelDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.load() }
    val actions =
        remember(viewModel, navigator, context) {
            ManageSubscriptionActions(
                onBack = { navigator.back() },
                onOpenPlay = { context.openPlaySubscriptions() },
                onRestore = viewModel::onRestore,
                onRetry = viewModel::load,
                onSubscribe = {
                    navigator.navigateReplacing(
                        Paywall(PAYWALL_SOURCE_MANAGE),
                        ManageSubscription::class
                    )
                },
                onDismissMessage = viewModel::onDismissMessage
            )
        }
    ManageSubscriptionScreen(
        state = state,
        actions = actions,
        modifier = modifier,
        showCancelDialog = showCancelDialog,
        onShowCancelDialog = { showCancelDialog = it }
    )
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Context.openPlaySubscriptions() {
    try {
        startActivity(PlayStoreLinks.manageSubscriptionIntent(this))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.plus_open_play_failed, Toast.LENGTH_LONG).show()
    }
}
