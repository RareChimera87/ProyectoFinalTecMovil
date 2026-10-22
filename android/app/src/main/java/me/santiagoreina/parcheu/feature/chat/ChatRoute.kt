package me.santiagoreina.parcheu.feature.chat

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Chat
import me.santiagoreina.parcheu.navigation.PrivacySettings
import me.santiagoreina.parcheu.navigation.Report
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Punto de entrada de la ruta [Chat] (F9, E5.2). */
@Composable
fun ChatRoute(route: Chat, navigator: AppNavigator, modifier: Modifier = Modifier) {
    val viewModel: ChatViewModel = koinViewModel(key = route.chatId) { parametersOf(route.chatId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChatEffect.OpenReport ->
                    navigator.navigate(Report(userId = effect.userId, chatId = effect.chatId))
                is ChatEffect.MuteFailed ->
                    Toast.makeText(context, errorMessage(effect.error), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val actions =
        remember(viewModel, navigator) {
            ChatActions(
                onBack = { navigator.back() },
                onDraftChange = viewModel::onDraftChange,
                onSend = viewModel::send,
                onRetryMessage = viewModel::retry,
                onToggleMute = viewModel::toggleMute,
                onReport = viewModel::report,
                onPrivacy = { navigator.navigate(PrivacySettings) },
                onDismissConsent = viewModel::dismissConsentBanner,
                onRetryLoad = viewModel::retryLoad
            )
        }
    ChatScreen(state = state, actions = actions, modifier = modifier)
}
