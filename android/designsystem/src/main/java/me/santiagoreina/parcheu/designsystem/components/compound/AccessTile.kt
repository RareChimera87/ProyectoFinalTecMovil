package me.santiagoreina.parcheu.designsystem.components.compound

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.theme.ParcheURadius
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Peso visual de un [AccessTile]: tonal (prc/onprc, el principal) o con borde de 1 dp (secundario). */
enum class AccessTileStyle { Tonal, Outline }

/**
 * Tarjeta de "acceso" de la dirección A: un atajo (Match al azar, Presentar, Invitar) con radio 18,
 * ícono arriba, título Sora 13/600 y [subtitle] opcional. Dos pesos ([style]) para que dos tiles vecinos
 * no pesen igual. [content] va al final (p. ej. un botón pequeño). Con [onClick] todo el tile es botón.
 */
@Composable
fun AccessTile(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: Painter? = null,
    style: AccessTileStyle = AccessTileStyle.Tonal,
    onClick: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val colors = ParcheUTheme.colors
    val shape = RoundedCornerShape(ParcheURadius.Card)
    val (container, titleColor, subtitleColor) =
        when (style) {
            AccessTileStyle.Tonal -> Triple(colors.prc, colors.onprc, colors.onprc)
            AccessTileStyle.Outline -> Triple(colors.s1, colors.tx, colors.mu)
        }
    Column(
        modifier
            .defaultMinSize(minHeight = 82.dp)
            .clip(shape)
            .background(container, shape)
            .then(
                if (style == AccessTileStyle.Outline) {
                    Modifier.border(BorderStroke(1.dp, colors.ln), shape)
                } else {
                    Modifier
                }
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(PaddingValues(horizontal = 12.dp, vertical = 11.dp)),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = if (style == AccessTileStyle.Tonal) titleColor else colors.prt,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = titleColor
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor
            )
        }
        content?.invoke(this)
    }
}
