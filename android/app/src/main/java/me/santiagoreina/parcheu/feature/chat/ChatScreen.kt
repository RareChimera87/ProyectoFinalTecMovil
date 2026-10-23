package me.santiagoreina.parcheu.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChatTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUIconButton
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Acciones de F9 que la pantalla delega (la ruta las conecta al ViewModel y a la navegación). */
data class ChatActions(
    val onBack: () -> Unit = {},
    val onDraftChange: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onRetryMessage: (String) -> Unit = {},
    val onToggleMute: () -> Unit = {},
    val onReport: () -> Unit = {},
    val onRetryLive: () -> Unit = {},
    val onPrivacy: () -> Unit = {},
    val onDismissConsent: () -> Unit = {},
    val onRetryLoad: () -> Unit = {}
)

/** F9 · Chat en vivo. Sin estado propio salvo el menú: todo llega en [state]. */
@Composable
fun ChatScreen(state: ChatUiState, actions: ChatActions, modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    val header = state.header
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize().background(colors.bg).imePadding()) {
        ParcheUChatTopBar(
            name = header?.name ?: "Chat",
            navigationIcon = rememberVectorPainter(BackIcon),
            navigationContentDescription = "Volver",
            onNavigationClick = actions.onBack,
            avatar = {
                if (header != null) {
                    ChatAvatar(header.initials, header.name, header.anonymous, size = 36.dp)
                }
            },
            subtitle = if (header?.muted == true) "Silenciado" else null,
            actions = {
                Box {
                    ParcheUIconButton(
                        icon = rememberVectorPainter(MoreVertIcon),
                        contentDescription = "Más opciones",
                        onClick = { menuOpen = true },
                        modifier = Modifier.testTag(ChatTags.MENU),
                        enabled = header != null
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(if (header?.muted == true) "Quitar silencio" else "Silenciar")
                            },
                            onClick = {
                                menuOpen = false
                                actions.onToggleMute()
                            },
                            modifier = Modifier.testTag(ChatTags.MENU_MUTE)
                        )
                        DropdownMenuItem(
                            text = { Text("Reportar o bloquear") },
                            enabled = header?.canReport == true,
                            onClick = {
                                menuOpen = false
                                actions.onReport()
                            },
                            modifier = Modifier.testTag(ChatTags.MENU_REPORT)
                        )
                    }
                }
            }
        )
        if (state.consentBannerVisible && header != null) {
            ConsentBanner(onSettings = actions.onPrivacy, onDismiss = actions.onDismissConsent)
        }
        state.headerError?.let { InlineError(it, onRetry = actions.onRetryLoad) }
        state.liveError?.let { InlineError(it, onRetry = actions.onRetryLive) }
        MessageList(state, actions, Modifier.weight(1f))
        if (state.header?.closed == true) {
            Text(
                text = "Este chat terminó. Ya no se pueden enviar mensajes.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.mu,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        } else {
            ChatInputBar(
                value = state.draft,
                onValueChange = actions.onDraftChange,
                onSend = actions.onSend,
                canSend = state.canSend,
                enabled = true,
                placeholder = "Escribe un mensaje"
            )
        }
    }
}

@Composable
private fun MessageList(state: ChatUiState, actions: ChatActions, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    if (state.messages.isEmpty() && !state.loading) {
        // Con el listener caído no se afirma que no hay mensajes: el error y su Reintentar van arriba.
        if (state.liveError != null) return
        Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Aún no hay mensajes. Rompe el hielo.",
                style = MaterialTheme.typography.bodyMedium,
                color = ParcheUTheme.colors.mu,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().testTag(ChatTags.MESSAGES),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.messages, key = { it.key }) { message ->
            if (message.system) {
                SystemMessage(message.displayText)
            } else {
                ChatBubble(message, onRetry = { actions.onRetryMessage(message.key) })
            }
        }
    }
}

@Composable
private fun InlineError(error: AppError, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = errorMessage(error),
            style = MaterialTheme.typography.bodySmall,
            color = ParcheUTheme.colors.coralt
        )
        ParcheUTextButton(
            text = "Reintentar",
            onClick = onRetry,
            size = me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize.Small
        )
    }
}

/** Mensaje de error para la persona (los textos finales los afina I2 con ux-copy). */
internal fun errorMessage(error: AppError): String = when (error) {
    AppError.Network -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
    AppError.Blocked -> "No puedes escribir en este chat."
    AppError.Forbidden -> "Ya no tienes acceso a este chat."
    AppError.Suspended -> "Tu cuenta está suspendida."
    AppError.ScenarioEnded -> "El escenario terminó."
    AppError.NotFound -> "No encontramos este chat."
    else -> "Algo salió mal. Inténtalo de nuevo."
}
