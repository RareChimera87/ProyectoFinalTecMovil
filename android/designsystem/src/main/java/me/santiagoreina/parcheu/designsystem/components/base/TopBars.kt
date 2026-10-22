package me.santiagoreina.parcheu.designsystem.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.theme.ParcheUSizes
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Botón de icono de 48 x 48 para las barras. [contentDescription] es obligatorio (no hay texto visible). */
@Composable
fun ParcheUIconButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = ParcheUTheme.colors
    Box(
        modifier =
        modifier
            .size(ParcheUSizes.IconButton)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = if (enabled) colors.tx else colors.tx.disabled(),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Marco de 56dp con el inset de la barra de estado. Los cuatro tipos de barra lo comparten. */
@Composable
private fun BarFrame(
    modifier: Modifier,
    start: @Composable RowScope.() -> Unit,
    center: @Composable RowScope.() -> Unit,
    end: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .background(ParcheUTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(ParcheUSizes.AppBar)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        start()
        center()
        end()
    }
}

@Composable
private fun RowScope.BarTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = ParcheUTheme.colors.tx,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
    )
}

/**
 * Barra de Inicio (C1): wordmark a la izquierda y, a la derecha, acciones (campana) y avatar de 34dp.
 * [wordmark] es un slot para no depender del recurso de marca de E1.2.
 */
@Composable
fun ParcheUHomeTopBar(
    wordmark: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    BarFrame(
        modifier = modifier,
        start = { Box(Modifier.padding(start = 12.dp)) { wordmark() } },
        center = { Spacer(Modifier.weight(1f)) },
        end = {
            actions()
            Spacer(Modifier.width(12.dp))
        },
    )
}

/** Barra de pantalla raíz: título Sora 19/600 y hasta dos iconos de acción. */
@Composable
fun ParcheURootTopBar(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    BarFrame(
        modifier = modifier,
        start = { Spacer(Modifier.width(12.dp)) },
        center = { BarTitle(title) },
        end = actions,
    )
}

/**
 * Barra de pantalla hija: botón de atrás o cerrar, título y una acción (texto o insignia) en [action].
 * [navigationIcon] es el painter de back o close.
 */
@Composable
fun ParcheUChildTopBar(
    title: String,
    navigationIcon: Painter,
    navigationContentDescription: String,
    onNavigationClick: () -> Unit,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit = {},
) {
    BarFrame(
        modifier = modifier,
        start = {
            ParcheUIconButton(navigationIcon, navigationContentDescription, onNavigationClick)
        },
        center = { BarTitle(title) },
        end = {
            action()
            Spacer(Modifier.width(8.dp))
        },
    )
}

/** Barra de chat: botón de atrás, avatar, nombre, subtítulo opcional y acciones (more_vert, flag). */
@Composable
fun ParcheUChatTopBar(
    name: String,
    navigationIcon: Painter,
    navigationContentDescription: String,
    onNavigationClick: () -> Unit,
    avatar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    BarFrame(
        modifier = modifier,
        start = {
            ParcheUIconButton(navigationIcon, navigationContentDescription, onNavigationClick)
            avatar()
        },
        center = {
            Column(
                Modifier.weight(1f).padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = ParcheUTheme.colors.tx,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = ParcheUTheme.colors.mu,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        end = actions,
    )
}
