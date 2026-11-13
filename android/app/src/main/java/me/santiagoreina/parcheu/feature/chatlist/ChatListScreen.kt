package me.santiagoreina.parcheu.feature.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.contract.ChatSummary
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.core.ui.StateIllustration
import me.santiagoreina.parcheu.core.ui.StateProp
import me.santiagoreina.parcheu.core.ui.UiState
import me.santiagoreina.parcheu.core.ui.stateProp
import me.santiagoreina.parcheu.designsystem.components.base.ModeSpineCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheURootTopBar
import me.santiagoreina.parcheu.designsystem.components.compound.EmptyState
import me.santiagoreina.parcheu.designsystem.components.compound.ErrorState
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.chat.ChatAvatar
import me.santiagoreina.parcheu.feature.chat.ChatTags
import me.santiagoreina.parcheu.feature.chat.HIDDEN_MESSAGE_TEXT
import me.santiagoreina.parcheu.feature.chat.errorMessage
import me.santiagoreina.parcheu.feature.chat.rememberTimeLabel
import me.santiagoreina.parcheu.feature.modes.colors

/** Lista de chats: filas con avatar, última línea, hora y no leídos; vacío, cargando y error. */
@Composable
fun ChatListScreen(
    state: UiState<List<ChatSummary>>,
    onOpenChat: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.bg).testTag(ChatTags.LIST_ROOT)) {
        ParcheURootTopBar(title = "Chats")
        when (state) {
            UiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
            UiState.Empty ->
                EmptyState(
                    title = "Aún no tienes chats",
                    message = "Cuando conectes con alguien, el chat aparece aquí.",
                    illustration = { StateIllustration(StateProp.Chat) },
                    modifier = Modifier.fillMaxSize().testTag(ChatTags.LIST_EMPTY)
                )
            is UiState.Error ->
                ErrorState(
                    title = "No pudimos cargar tus chats",
                    message = errorMessage(state.error),
                    retryLabel = "Reintentar",
                    onRetryClick = onRetry,
                    illustration = { StateIllustration(state.error.stateProp(), error = true) },
                    modifier = Modifier.fillMaxSize().testTag(ChatTags.LIST_ERROR)
                )
            is UiState.Loaded ->
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(ParcheUTheme.spacing.blockGapTight)
                ) {
                    items(state.data, key = { it.id }) { chat ->
                        ChatRow(chat, onClick = { onOpenChat(chat.id) })
                    }
                }
        }
    }
}

/**
 * Fila de la lista: tarjeta con el lomo del modo (dirección A). El modo también va en texto ("Amistad"),
 * porque el lomo y el tono del avatar son decorativos.
 */
@Composable
private fun ChatRow(chat: ChatSummary, onClick: () -> Unit) {
    val colors = ParcheUTheme.colors
    val mode = chat.mode.colors()
    val anonymous = chat.other.id == null && chat.other.anonId != null
    val last = chat.lastMessage
    val preview =
        when {
            last == null -> "Sin mensajes todavía"
            last.hidden -> HIDDEN_MESSAGE_TEXT
            else -> last.text
        }
    val time = rememberTimeLabel(last?.createdAt)
    val unread = chat.unread > 0
    ModeSpineCard(
        mode = mode,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(ChatTags.row(chat.id))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChatAvatar(chat.other.initials, chat.other.label, anonymous, size = 44.dp, mode = mode)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.other.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.tx,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (time.isNotEmpty()) {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.mu,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = if (last?.hidden ==
                            true
                        ) {
                            FontStyle.Italic
                        } else {
                            FontStyle.Normal
                        },
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (unread) colors.tx else colors.mu,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (unread) {
                        Box(
                            modifier =
                            Modifier
                                .padding(start = 8.dp)
                                .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                                .clip(CircleShape)
                                .background(colors.primary, CircleShape)
                                .semantics { contentDescription = "${chat.unread} sin leer" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (chat.unread > 99) "99+" else chat.unread.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                        }
                    }
                }
                Text(
                    text =
                    listOfNotNull(modeLabel(chat.mode), "Silenciado".takeIf { chat.muted })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mu
                )
            }
        }
    }
}

private fun modeLabel(mode: Mode): String = when (mode) {
    Mode.AMISTAD -> "Amistad"
    Mode.PAREJA -> "Pareja"
    Mode.ESTUDIO -> "Estudio"
}
