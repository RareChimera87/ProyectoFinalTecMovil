package me.santiagoreina.parcheu.designsystem.components.base

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.theme.ParcheURadius
import me.santiagoreina.parcheu.designsystem.theme.ParcheUSizes
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Tamaños de botón del inventario §3.5: 52 (radio 16), 40 y 36 compactos (radio 12). */
enum class ParcheUButtonSize(val height: androidx.compose.ui.unit.Dp, val radius: androidx.compose.ui.unit.Dp) {
    Large(ParcheUSizes.Button, ParcheURadius.Button),
    Medium(ParcheUSizes.ButtonCompact, ParcheURadius.ButtonCompact),
    Small(ParcheUSizes.ButtonSmall, ParcheURadius.ButtonCompact),
}

/** Botón primario: violeta #6D4AFF con texto blanco 15/600 (5,15:1). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ParcheUButtonSize = ParcheUButtonSize.Large,
    enabled: Boolean = true,
    icon: Painter? = null,
) {
    val colors = ParcheUTheme.colors
    ButtonBase(
        text = text,
        onClick = onClick,
        modifier = modifier,
        size = size,
        enabled = enabled,
        icon = icon,
        container = colors.primary,
        content = colors.onPrimary,
        border = null,
    )
}

/** Botón tonal: prc / onprc. */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ParcheUButtonSize = ParcheUButtonSize.Large,
    enabled: Boolean = true,
    icon: Painter? = null,
) {
    val colors = ParcheUTheme.colors
    ButtonBase(
        text = text,
        onClick = onClick,
        modifier = modifier,
        size = size,
        enabled = enabled,
        icon = icon,
        container = colors.prc,
        content = colors.onprc,
        border = null,
    )
}

/** Botón con borde: 1,5dp ln y texto tx. */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ParcheUButtonSize = ParcheUButtonSize.Large,
    enabled: Boolean = true,
    icon: Painter? = null,
) {
    val colors = ParcheUTheme.colors
    ButtonBase(
        text = text,
        onClick = onClick,
        modifier = modifier,
        size = size,
        enabled = enabled,
        icon = icon,
        container = Color.Transparent,
        content = colors.tx,
        border = colors.ln,
    )
}

/**
 * Botón de texto: el color es `prt` (el violeta puro no llega a AA como texto). Con [ParcheUButtonSize.Small]
 * es la "acción de texto" 13/600 de las barras y los encabezados.
 */
@Composable
fun ParcheUTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ParcheUButtonSize = ParcheUButtonSize.Large,
    enabled: Boolean = true,
    icon: Painter? = null,
) {
    ButtonBase(
        text = text,
        onClick = onClick,
        modifier = modifier,
        size = size,
        enabled = enabled,
        icon = icon,
        container = Color.Transparent,
        content = ParcheUTheme.colors.prt,
        border = null,
    )
}

/**
 * Botón "Continuar con Google": fondo blanco y texto #1A1626 en ambos temas. [logo] debe ser el recurso
 * oficial de Google (ver docs/pendientes-humanos.md, E1.3); mientras no se entregue se dibuja una "G".
 */
@Composable
fun GoogleSignInButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    logo: Painter? = null,
) {
    val colors = ParcheUTheme.colors
    val content = if (enabled) GoogleButtonText else GoogleButtonText.disabled()
    val container = if (enabled) colors.white else colors.white.disabled()
    ButtonFrame(
        onClick = onClick,
        modifier = modifier,
        size = ParcheUButtonSize.Large,
        enabled = enabled,
        container = container,
        border = if (colors.isDark) null else colors.ln,
    ) {
        if (logo != null) {
            Icon(
                painter = logo,
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = "G",
                style = MaterialTheme.typography.titleSmall,
                color = GoogleBlue,
                modifier = Modifier.width(20.dp),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val GoogleButtonText = Color(0xFF1A1626)
private val GoogleBlue = Color(0xFF4285F4)

@Composable
private fun ButtonBase(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    size: ParcheUButtonSize,
    enabled: Boolean,
    icon: Painter?,
    container: Color,
    content: Color,
    border: Color?,
) {
    val colors = ParcheUTheme.colors
    val filled = container != Color.Transparent
    val resolvedContainer =
        when {
            !filled -> Color.Transparent
            enabled -> container
            else -> colors.tx.copy(alpha = 0.12f)
        }
    val resolvedContent = if (enabled) content else colors.tx.disabled()
    val textStyle =
        if (size == ParcheUButtonSize.Small) {
            MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        } else {
            MaterialTheme.typography.labelLarge
        }
    ButtonFrame(
        onClick = onClick,
        modifier = modifier,
        size = size,
        enabled = enabled,
        container = resolvedContainer,
        border = border?.let { if (enabled) it else it.copy(alpha = 0.5f) },
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = resolvedContent,
                modifier = Modifier.size(if (size == ParcheUButtonSize.Large) 20.dp else 18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = textStyle,
            color = resolvedContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Marco común: el nodo clicable mide al menos 48dp de alto (área táctil) y el botón se dibuja centrado
 * con la altura del inventario.
 */
@Composable
private fun ButtonFrame(
    onClick: () -> Unit,
    modifier: Modifier,
    size: ParcheUButtonSize,
    enabled: Boolean,
    container: Color,
    border: Color?,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(size.radius)
    Box(
        modifier =
        modifier
            .touchTarget()
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        propagateMinConstraints = true,
    ) {
        Row(
            modifier =
            Modifier
                .requiredHeight(size.height)
                .clip(shape)
                .background(container, shape)
                .then(
                    if (border !=
                        null
                    ) {
                        Modifier.border(BorderStroke(1.5.dp, border), shape)
                    } else {
                        Modifier
                    },
                )
                .padding(
                    PaddingValues(
                        horizontal = if (size ==
                            ParcheUButtonSize.Large
                        ) {
                            20.dp
                        } else {
                            14.dp
                        },
                    ),
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.labelLarge) {
                content()
            }
        }
    }
}
