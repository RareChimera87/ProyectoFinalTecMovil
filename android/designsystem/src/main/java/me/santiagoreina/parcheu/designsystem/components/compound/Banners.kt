package me.santiagoreina.parcheu.designsystem.components.compound

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.theme.ParcheURadius
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Banner tonal prc / onprc de radio 16 con icono, texto y una acción de texto opcional. */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    val colors = ParcheUTheme.colors
    val shape = RoundedCornerShape(ParcheURadius.FieldLarge)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.prc, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = colors.onprc,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onprc,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onActionClick != null) {
            ParcheUTextButton(
                text = actionLabel,
                onClick = onActionClick,
                size = ParcheUButtonSize.Small
            )
        }
    }
}

/** Nota discreta s2 de radio 12 (aclaraciones al pie de un formulario o una tarjeta). */
@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier, icon: Painter? = null) {
    val colors = ParcheUTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.s2, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.mu, modifier = Modifier.size(18.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.mu,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Aviso de consentimiento s1 de radio 12 con borde ln: el texto legal y, opcionalmente, un enlace de texto
 * (por ejemplo "Política de privacidad") que abre el documento (ADR-0017).
 */
@Composable
fun ConsentBanner(
    text: String,
    modifier: Modifier = Modifier,
    linkLabel: String? = null,
    onLinkClick: (() -> Unit)? = null
) {
    val colors = ParcheUTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.s1, shape)
            .border(1.dp, colors.ln, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = colors.tx)
        if (linkLabel != null && onLinkClick != null) {
            ParcheUTextButton(
                text = linkLabel,
                onClick = onLinkClick,
                size = ParcheUButtonSize.Small
            )
        }
    }
}

/** Fila de [InfoListCard]: icono opcional prt y texto. */
@Immutable
data class InfoListItem(val text: String, val icon: Painter? = null)

/** Tarjeta con encabezado y filas con icono prt / texto mu (B4, E2, E3, F8). */
@Composable
fun InfoListCard(title: String, items: List<InfoListItem>, modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    ParcheUCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.tx
        )
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (item.icon != null) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = colors.prt,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mu,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
