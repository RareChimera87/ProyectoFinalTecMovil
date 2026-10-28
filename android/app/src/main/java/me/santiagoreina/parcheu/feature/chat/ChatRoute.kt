package me.santiagoreina.parcheu.feature.chat

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.feature.safety.CloseReason
import me.santiagoreina.parcheu.feature.safety.SafetySheetHost
import me.santiagoreina.parcheu.feature.safety.SafetyTarget
import me.santiagoreina.parcheu.feature.safety.toReportRoute
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.Chat
import me.santiagoreina.parcheu.navigation.PrivacySettings
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Punto de entrada de la ruta [Chat] (F9, E5.2). */
@Composable
fun ChatRoute(route: Chat, navigator: AppNavigator, modifier: Modifier = Modifier) {
    ChatContent(route.chatId, navigator, modifier)
}

/**
 * Chat de [chatId] con sus acciones (silenciar, reportar, bloquear). F9 lo usa tal cual; F3 (E4.8) lo reutiliza
 * con [pinned] (tarjeta del escenario) y [footer] (acuerdo).
 */
@Composable
fun ChatContent(
    chatId: String,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    pinned: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {}
) {
    val viewModel: ChatViewModel = koinViewModel(key = chatId) { parametersOf(chatId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var safetyTarget by remember { mutableStateOf<SafetyTarget?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChatEffect.OpenReport -> safetyTarget = effect.target
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
                onRetryLive = viewModel::retryLive,
                onPrivacy = { navigator.navigate(PrivacySettings) },
                onDismissConsent = viewModel::dismissConsentBanner,
                onRetryLoad = viewModel::retryLoad
            )
        }
    ChatScreen(
        state = state,
        actions = actions,
        modifier = modifier,
        pinned = pinned,
        footer = footer
    )
    // H1 (E8.1): hoja sobre el chat con Silenciar, Reportar y Bloquear.
    safetyTarget?.let { target ->
        SafetySheetHost(target) { reason, sheet ->
            safetyTarget = null
            when (reason) {
                CloseReason.Report -> navigator.navigate(target.toReportRoute())
                CloseReason.Blocked -> navigator.back()
                CloseReason.Muted -> viewModel.onMutedChanged(sheet.muted)
                CloseReason.Dismissed -> Unit
            }
        }
    }
}
