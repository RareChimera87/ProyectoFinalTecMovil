package me.santiagoreina.parcheu.feature.presentar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Chat
import me.santiagoreina.parcheu.navigation.IntroProposal
import me.santiagoreina.parcheu.navigation.IntroResult
import me.santiagoreina.parcheu.navigation.Introduce
import me.santiagoreina.parcheu.navigation.Paywall
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Origen del paywall cuando se abre desde E1 o E2. */
const val PRESENTAR_PAYWALL_SOURCE = "introduce"

/** E1 (E6.2). Al crear la presentación abre E2 sacando E1 de la pila. */
@Composable
fun IntroduceEntry(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: IntroduceViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is IntroduceEffect.OpenResult ->
                    navigator.navigateReplacing(
                        IntroResult(effect.introductionId),
                        Introduce::class
                    )
                IntroduceEffect.OpenPaywall -> navigator.navigate(Paywall(PRESENTAR_PAYWALL_SOURCE))
            }
        }
    }
    IntroduceScreen(
        state = state,
        actions = IntroduceActions(
            onBack = { navigator.back() },
            onMode = viewModel::onMode,
            onToggle = viewModel::onToggle,
            onSubmit = viewModel::onSubmit,
            onSeePlus = viewModel::onSeePlus,
            onRetry = viewModel::onRetry,
            onDismissNotice = viewModel::onDismissNotice
        ),
        modifier = modifier
    )
}

/** E2 (E6.2). */
@Composable
fun IntroResultEntry(
    id: String,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: IntroResultViewModel = koinViewModel(key = "intro-result-$id") { parametersOf(id) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                IntroResultEffect.Close -> navigator.back()
                IntroResultEffect.OpenPaywall -> navigator.navigate(
                    Paywall(PRESENTAR_PAYWALL_SOURCE)
                )
                IntroResultEffect.IntroduceAgain -> navigator.navigateReplacing(
                    Introduce,
                    IntroResult::class
                )
            }
        }
    }
    IntroResultScreen(
        state = state,
        actions = IntroResultActions(
            onBack = { navigator.back() },
            onRetry = viewModel::onRetry,
            onUnlock = viewModel::onUnlock,
            onSeePlus = viewModel::onSeePlus,
            onSend = viewModel::onSend,
            onSkip = viewModel::onSkip,
            onDone = viewModel::onDone,
            onIntroduceAgain = viewModel::onIntroduceAgain,
            onDismissNotice = viewModel::onDismissNotice
        ),
        modifier = modifier
    )
}

/** E3 (E6.2). Cuando los dos aceptan abre el chat F9. */
@Composable
fun IntroProposalEntry(
    id: String,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: IntroProposalViewModel =
        koinViewModel(key = "intro-proposal-$id") { parametersOf(id) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is IntroProposalEffect.OpenChat -> navigator.navigateReplacing(
                    Chat(effect.chatId),
                    IntroProposal::class
                )
            }
        }
    }
    IntroProposalScreen(
        state = state,
        actions = IntroProposalActions(
            onClose = { navigator.back() },
            onRetry = viewModel::onRetry,
            onInterested = viewModel::onInterested,
            onDecline = viewModel::onDecline,
            onOpenChat = viewModel::onOpenChat,
            onDismissError = viewModel::onDismissError
        ),
        modifier = modifier
    )
}
