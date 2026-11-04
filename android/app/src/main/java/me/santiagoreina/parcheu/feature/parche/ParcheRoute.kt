package me.santiagoreina.parcheu.feature.parche

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.feature.safety.SafetyEntryHost
import me.santiagoreina.parcheu.feature.safety.SafetyTarget
import me.santiagoreina.parcheu.navigation.AddFriends
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Communities
import me.santiagoreina.parcheu.navigation.Evolution
import me.santiagoreina.parcheu.navigation.FriendsOfFriends
import me.santiagoreina.parcheu.navigation.Introduce
import me.santiagoreina.parcheu.navigation.MatchQueue
import me.santiagoreina.parcheu.navigation.Parche
import me.santiagoreina.parcheu.navigation.Paywall
import org.koin.compose.viewmodel.koinViewModel

/** Origen del paywall cuando se abre desde D1 (analítica de G1). */
const val PAYWALL_SOURCE_PARCHE = "parche"

/**
 * D1-D5 (E3.4). [Parche.community] llega de D8 y filtra el grafo; las funciones de ParcheU+
 * (comunidades, evolución) llevan al paywall G1 cuando la persona no tiene el plan. El banner de
 * bloqueo y la entrada de ParcheU+ abren amigos de amigos (D7 o D6, E6.3).
 */
@Composable
fun ParcheRoute(
    route: Parche,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: ParcheViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var safetyTarget by remember { mutableStateOf<SafetyTarget?>(null) }
    LaunchedEffect(route.community) { viewModel.start(route.community) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ParcheEffect.Navigate -> navigator.navigate(effect.route)
            }
        }
    }
    val plus = (state as? ParcheUiState.Loaded)?.content?.plus == true
    val callbacks =
        remember(viewModel, navigator, plus) {
            ParcheCallbacks(
                onMode = viewModel::onModeFilter,
                onAffinity = viewModel::onAffinityFilter,
                onCommunity = {
                    val loaded = viewModel.state.value as? ParcheUiState.Loaded
                    when {
                        loaded?.filters?.communityId != null -> viewModel.onClearCommunity()
                        plus -> navigator.navigate(Communities)
                        else -> navigator.navigate(Paywall(PAYWALL_SOURCE_PARCHE))
                    }
                },
                onNodeClick = viewModel::onNodeClick,
                onDismissSheet = viewModel::onDismissSheet,
                onSimulateAgain = viewModel::onSimulateAgain,
                onChat = viewModel::onChat,
                onViewResult = viewModel::onViewResult,
                onRetry = viewModel::retry,
                onTimeline = {
                    navigator.navigate(if (plus) Evolution else Paywall(PAYWALL_SOURCE_PARCHE))
                },
                onAddFriends = { navigator.navigate(AddFriends()) },
                onRandomMatch = { navigator.navigate(MatchQueue()) },
                // Flujo 11 del inventario: el banner de D1 lleva a D7 (gratis), que ofrece G1.
                onUnlock = { navigator.navigate(FriendsOfFriends) },
                onFriendsOfFriends = { navigator.navigate(FriendsOfFriends) },
                onIntroduce = { navigator.navigate(Introduce) },
                onReportOrBlock = {
                    // E8.7: persona de la hoja D2, con el id con el que se la ve (uuid o `anonId`).
                    val sheet = (viewModel.state.value as? ParcheUiState.Loaded)?.sheet
                    if (sheet != null) {
                        safetyTarget = SafetyTarget(
                            id = sheet.node.id,
                            name = if (sheet.node.anonymous) "Alguien" else sheet.node.label,
                            anonymous = sheet.node.anonymous,
                            chatId = sheet.detail?.chatId
                        )
                    }
                }
            )
        }
    ParcheScreen(state = state, callbacks = callbacks, modifier = modifier)
    // H1 sobre D2: al bloquear se cierra la hoja y se recarga el grafo (la persona desaparece).
    SafetyEntryHost(
        target = safetyTarget,
        navigator = navigator,
        onClear = { safetyTarget = null },
        onBlocked = {
            viewModel.onDismissSheet()
            viewModel.retry()
        }
    )
}
