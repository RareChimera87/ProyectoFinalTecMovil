package me.santiagoreina.parcheu.designsystem.components.compound

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.components.base.disabled
import me.santiagoreina.parcheu.designsystem.components.base.touchTarget
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/**
 * Fila de ajustes (G4, H1, H3): mínimo 56dp, caja de icono de 40dp y radio 12, título, subtítulo opcional y
 * un [trailing] (switch, flecha, valor). La variante [destructive] usa el token `error` para el icono y el
 * texto (Reportar, Bloquear, Borrar cuenta); el color nunca va solo, el título siempre dice la acción.
 */
@Composable
fun SettingsRow(
    title: String,
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = ParcheUTheme.colors
    val titleColor = if (destructive) colors.error else colors.tx
    val iconTint = if (destructive) colors.error else colors.prt
    val boxColor = if (destructive) colors.error.copy(alpha = 0.14f) else colors.s2
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .touchTarget()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(boxColor, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = if (enabled) iconTint else iconTint.disabled(),
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (enabled) titleColor else titleColor.disabled(),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) colors.mu else colors.mu.disabled(),
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** Círculo con icono (C1, C2): [size] 40dp por defecto sobre [container]. */
@Composable
fun IconCircle(
    icon: Painter,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    container: Color = ParcheUTheme.colors.prc,
    tint: Color = ParcheUTheme.colors.onprc,
) {
    Box(
        modifier = modifier.size(size).background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * Fila del centro de notificaciones (C2): [leading] (IconCircle o avatar), título, cuerpo, hora y un punto
 * no leído de 8dp. El estado de lectura se anuncia en la descripción ([unreadLabel]), no solo con el punto.
 */
@Composable
fun NotificationRow(
    title: String,
    body: String,
    time: String,
    unread: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    unreadLabel: String = "No leída",
) {
    val colors = ParcheUTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .touchTarget()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                if (unread) contentDescription = "$unreadLabel. $title. $body. $time"
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = colors.tx,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = colors.mu,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = time, style = MaterialTheme.typography.labelSmall, color = colors.mu)
        }
        if (unread) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .background(colors.prt, CircleShape)
                    .clearAndSetSemantics { },
            )
        }
    }
}
