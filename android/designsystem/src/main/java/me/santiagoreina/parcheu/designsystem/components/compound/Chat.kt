package me.santiagoreina.parcheu.designsystem.components.compound

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.components.base.disabled
import me.santiagoreina.parcheu.designsystem.components.base.touchTarget
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Quién habla en una burbuja: la otra persona, el usuario o el gemelo (simulaciones y consejos). */
enum class ChatBubbleKind { Other, Own, Twin }

/**
 * Burbuja de chat. Ajena: s2 con radio 20 20 20 6; propia: violeta #6D4AFF con radio 20 20 6 20; gemelo:
 * prc / onprc con radio 18 18 6 18. La propia se alinea a la derecha y las demás a la izquierda; el ancho
 * máximo es 280dp para que quede aire en pantallas pequeñas.
 */
@Composable
fun ChatBubble(
    text: String,
    kind: ChatBubbleKind,
    modifier: Modifier = Modifier,
    timestamp: String? = null
) {
    val colors = ParcheUTheme.colors
    val (container, content, shape) = when (kind) {
        ChatBubbleKind.Other -> Triple(
            colors.s2,
            colors.tx,
            RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomEnd = 20.dp,
                bottomStart = 6.dp
            )
        )
        ChatBubbleKind.Own -> Triple(
            colors.primary,
            colors.onPrimary,
            RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomEnd = 6.dp,
                bottomStart = 20.dp
            )
        )
        ChatBubbleKind.Twin -> Triple(
            colors.prc,
            colors.onprc,
            RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomEnd = 18.dp,
                bottomStart = 6.dp
            )
        )
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (kind ==
            ChatBubbleKind.Own
        ) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(container, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = content)
            if (timestamp != null) {
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.72f)
                )
            }
        }
    }
}

/**
 * Barra de entrada del chat: campo en píldora de 48dp (s2) y botón de enviar circular de 48dp. El botón
 * se deshabilita con el texto vacío. [sendIcon] viene de E1.2 (send).
 */
@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    placeholder: String,
    sendIcon: Painter,
    modifier: Modifier = Modifier,
    sendContentDescription: String = "Enviar",
    enabled: Boolean = true
) {
    val colors = ParcheUTheme.colors
    val canSend = enabled && value.isNotBlank()
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
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
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = placeholder },
                decorationBox = { inner ->
                    // El nodo del campo mide lo que mide su decoración: 48dp mínimo de área táctil.
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
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (canSend) colors.primary else colors.primary.disabled(), CircleShape)
                .touchTarget()
                .clickable(enabled = canSend, role = Role.Button, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = sendIcon,
                contentDescription = sendContentDescription,
                tint = if (canSend) colors.onPrimary else colors.onPrimary.disabled(),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
