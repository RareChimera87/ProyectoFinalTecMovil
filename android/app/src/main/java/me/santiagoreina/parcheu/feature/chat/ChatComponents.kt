package me.santiagoreina.parcheu.feature.chat

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Date
import kotlin.time.Instant
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.designsystem.theme.affinityAlpha
import me.santiagoreina.parcheu.designsystem.theme.avatarColors
import me.santiagoreina.parcheu.designsystem.theme.onAvatarColor

/** Etiquetas de prueba de F9 y la lista de chats. */
object ChatTags {
    const val MESSAGES = "chat:messages"
    const val INPUT = "chat:input"
    const val SEND = "chat:send"
    const val MENU = "chat:menu"
    const val MENU_MUTE = "chat:menu:mute"
    const val MENU_REPORT = "chat:menu:report"
    const val CONSENT_BANNER = "chat:consent"
    const val BACK = "chat:back"
    const val RETRY = "chat:retry"

    fun bubble(key: String) = "chat:bubble:$key"

    const val LIST_ROOT = "chatlist:root"
    const val LIST_EMPTY = "chatlist:empty"
    const val LIST_ERROR = "chatlist:error"

    fun row(chatId: String) = "chatlist:row:$chatId"
}

/** Hora local "HH:mm" (o "h:mm a" según el sistema) de [instant]; vacío si aún no tiene hora. */
@Composable
internal fun rememberTimeLabel(instant: Instant?): String {
    if (instant == null) return ""
    val context = LocalContext.current
    return remember(instant) {
        DateFormat.getTimeFormat(context).format(Date(instant.toEpochMilliseconds()))
    }
}

/** Avatar de F9 y de la lista: iniciales sobre violeta (como G0) o silueta si la persona es anónima. */
@Composable
internal fun ChatAvatar(
    initials: String,
    name: String,
    anonymous: Boolean,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    if (anonymous) {
        Box(
            modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.s2, CircleShape)
                .border(1.5.dp, colors.prt, CircleShape)
                .clearAndSetSemantics { contentDescription = "Alguien" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = rememberVectorPainter(PersonIcon),
                contentDescription = null,
                tint = colors.prt,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    } else {
        val alpha = affinityAlpha(70)
        val palette = avatarColors(alpha, colors.isDark)
        Box(
            modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(palette.fill, CircleShape)
                .clearAndSetSemantics { contentDescription = name },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = onAvatarColor(alpha, colors.isDark),
                fontSize = (size.value * 0.38f).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/**
 * Burbuja de F9. Ajena: s2, radio 20 20 20 6; propia: violeta, radio 20 20 6 20. Mientras se envía se
 * atenúa; si falló muestra "No se envió" y "Reintentar". Un mensaje oculto muestra [HIDDEN_MESSAGE_TEXT].
 */
@Composable
internal fun ChatBubble(
    message: ChatMessageUi,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    val mine = message.mine
    val container = if (mine) colors.primary else colors.s2
    val content = if (mine) colors.onPrimary else colors.tx
    val shape =
        RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomEnd = if (mine) 6.dp else 20.dp,
            bottomStart = if (mine) 20.dp else 6.dp
        )
    val pending = message.status is MessageStatus.Pending
    val time = rememberTimeLabel(message.createdAt)
    Row(
        modifier = modifier.fillMaxWidth().testTag(ChatTags.bubble(message.key)),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            Column(
                modifier =
                Modifier
                    .widthIn(max = 280.dp)
                    .alpha(if (pending) 0.6f else 1f)
                    .clip(shape)
                    .background(container, shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = message.displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = if (message.hidden) FontStyle.Italic else FontStyle.Normal,
                    color = if (message.hidden) content.copy(alpha = 0.8f) else content
                )
                val caption =
                    when {
                        pending -> "Enviando…"
                        else -> time
                    }
                if (caption.isNotEmpty()) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = content.copy(alpha = 0.72f)
                    )
                }
            }
            val status = message.status
            if (status is MessageStatus.Failed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "No se envió",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.coralt
                    )
                    ParcheUTextButton(
                        text = "Reintentar",
                        onClick = onRetry,
                        size = ParcheUButtonSize.Small,
                        modifier = Modifier.testTag(ChatTags.RETRY)
                    )
                }
            }
        }
    }
}

/** Mensaje del sistema: texto centrado y discreto. */
@Composable
internal fun SystemMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = ParcheUTheme.colors.mu,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

/** Banner de consentimiento (F9): tonal prc / onprc, radio 16, con enlace a los ajustes de privacidad. */
@Composable
internal fun ConsentBanner(
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape)
            .background(colors.prc, shape)
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 4.dp)
            .testTag(ChatTags.CONSENT_BANNER)
    ) {
        Text(
            text =
            "Tu gemelo solo aprende de este chat si lo permitiste. " +
                "Puedes cambiarlo cuando quieras en Privacidad.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onprc,
            modifier = Modifier.padding(end = 8.dp)
        )
        Row {
            ParcheUTextButton(
                text = "Ir a Privacidad",
                onClick = onSettings,
                size = ParcheUButtonSize.Small
            )
            ParcheUTextButton(
                text = "Entendido",
                onClick = onDismiss,
                size = ParcheUButtonSize.Small
            )
        }
    }
}

/** Entrada de F9: píldora s2 de 48dp y botón de enviar circular de 48dp, deshabilitado sin texto. */
@Composable
internal fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    enabled: Boolean,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier =
            Modifier
                .weight(1f)
                .clip(CircleShape)
                .background(colors.s2, CircleShape)
                .border(1.dp, colors.ln, CircleShape)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.tx),
                cursorBrush = SolidColor(colors.prt),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(ChatTags.INPUT)
                    .semantics { contentDescription = placeholder },
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.mu
                            )
                        }
                        inner()
                    }
                }
            )
        }
        Box(
            modifier =
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.primary, CircleShape)
                .alpha(if (canSend) 1f else 0.38f)
                .testTag(ChatTags.SEND)
                .clickable(enabled = canSend, role = Role.Button, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = rememberVectorPainter(SendIcon),
                contentDescription = "Enviar",
                tint = colors.onPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
