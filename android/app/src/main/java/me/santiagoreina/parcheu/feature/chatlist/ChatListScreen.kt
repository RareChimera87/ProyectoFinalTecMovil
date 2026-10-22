package me.santiagoreina.parcheu.feature.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.contract.ChatSummary
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.core.ui.UiState
import me.santiagoreina.parcheu.designsystem.components.base.ParcheURootTopBar
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.chat.ChatAvatar
import me.santiagoreina.parcheu.feature.chat.ChatTags
import me.santiagoreina.parcheu.feature.chat.HIDDEN_MESSAGE_TEXT
import me.santiagoreina.parcheu.feature.chat.errorMessage
import me.santiagoreina.parcheu.feature.chat.rememberTimeLabel

/** Lista de chats: filas con avatar, última línea, hora y no leídos; vacío, cargando y error. */
@Composable
fun ChatListScreen(
    state: UiState<List<ChatSummary>>,
    onOpenChat: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
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
                CenteredMessage(
                    title = "Aún no tienes chats",
                    body = "Cuando conectes con alguien, el chat aparece aquí.",
                    modifier = Modifier.testTag(ChatTags.LIST_EMPTY),
                )
            is UiState.Error ->
                CenteredMessage(
                    title = "No pudimos cargar tus chats",
                    body = errorMessage(state.error),
                    modifier = Modifier.testTag(ChatTags.LIST_ERROR),
                ) {
                    PrimaryButton(text = "Reintentar", onClick = onRetry)
                }
            is UiState.Loaded ->
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.data, key = { it.id }) { chat ->
                        ChatRow(chat, onClick = { onOpenChat(chat.id) })
                    }
                }
        }
    }
}

@Composable
private fun CenteredMessage(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = ParcheUTheme.colors.tx,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = ParcheUTheme.colors.mu,
            textAlign = TextAlign.Center,
        )
        action()
    }
}

@Composable
private fun ChatRow(chat: ChatSummary, onClick: () -> Unit) {
    val colors = ParcheUTheme.colors
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
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .testTag(ChatTags.row(chat.id))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChatAvatar(chat.other.initials, chat.other.label, anonymous, size = 48.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chat.other.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.tx,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (time.isNotEmpty()) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mu,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = if (last?.hidden == true) FontStyle.Italic else FontStyle.Normal,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (unread) colors.tx else colors.mu,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (chat.muted) {
                    Text(
                        text = "Silenciado",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mu,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (unread) {
                    Box(
                        modifier =
                        Modifier
                            .padding(start = 8.dp)
                            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                            .clip(CircleShape)
                            .background(colors.primary, CircleShape)
                            .semantics { contentDescription = "${chat.unread} sin leer" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (chat.unread > 99) "99+" else chat.unread.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                }
            }
            Text(
                text = modeLabel(chat.mode),
                style = MaterialTheme.typography.labelSmall,
                color = modeColor(chat.mode),
            )
        }
    }
}

private fun modeLabel(mode: Mode): String = when (mode) {
    Mode.AMISTAD -> "Amistad"
    Mode.PAREJA -> "Pareja"
    Mode.ESTUDIO -> "Estudio"
}

@Composable
private fun modeColor(mode: Mode) = when (mode) {
    Mode.AMISTAD -> ParcheUTheme.colors.mintt
    Mode.PAREJA -> ParcheUTheme.colors.coralt
    Mode.ESTUDIO -> ParcheUTheme.colors.bluet
}
