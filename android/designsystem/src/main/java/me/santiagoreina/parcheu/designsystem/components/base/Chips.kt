package me.santiagoreina.parcheu.designsystem.components.base

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.designsystem.theme.ModeColors
import me.santiagoreina.parcheu.designsystem.theme.ParcheUSizes
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Dos tamaños de ModeChip (docs/mockups-inventario.md §3, conflicto 1): M para héroes y selección, S dentro de listas. */
enum class ModeChipSize(
    val height: Dp,
    val startPadding: Dp,
    val endPadding: Dp,
    val iconSize: Dp,
    val textSizeSp: Int
) {
    M(ParcheUSizes.ModeChipMedium, 8.dp, 11.dp, 16.dp, 13),
    S(ParcheUSizes.ModeChipSmall, 6.dp, 9.dp, 14.dp, 11)
}

/** Estilo de [ModeChip]: pleno (color del modo + tinta) o tonal (contenedor del modo + su texto AA). */
enum class ModeChipStyle { Solid, Tonal }

/**
 * Píldora con el color pleno del modo y texto #14111F. Con [ModeChipStyle.Tonal] usa el contenedor
 * tonal del modo (más calmo, para filas con lomo de color donde el pleno ya está en el lomo). No es
 * interactiva.
 */
@Composable
fun ModeChip(
    label: String,
    mode: ModeColors,
    modifier: Modifier = Modifier,
    size: ModeChipSize = ModeChipSize.M,
    icon: Painter? = null,
    style: ModeChipStyle = ModeChipStyle.Solid
) {
    val container = if (style == ModeChipStyle.Solid) mode.solid else mode.container
    val content = if (style == ModeChipStyle.Solid) mode.onSolid else mode.onContainer
    Row(
        modifier =
        modifier
            .height(size.height)
            .clip(ParcheUTheme.shapes.pill)
            .background(container)
            .padding(start = size.startPadding, end = size.endPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(size.iconSize)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            style =
            MaterialTheme.typography.labelMedium.copy(
                fontSize = size.textSizeSp.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Filtro de modo (Todos / Amistad / Pareja / Estudio): píldora de 32dp (dirección A); activo prc/onprc,
 * inactivo con borde ln. Con [dotColor] (el `solid` del modo) se antepone un punto de 7dp del color del
 * modo, como en el mockup A · Tu parche.
 */
@Composable
fun ModeFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Painter? = null,
    dotColor: Color? = null
) {
    val colors = ParcheUTheme.colors
    OutlinedChipFrame(
        selected = selected,
        enabled = enabled,
        modifier =
        modifier
            .touchTarget()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            ),
        label = label,
        icon = icon,
        iconSize = 16.dp,
        selectedContainer = colors.prc,
        selectedContent = colors.onprc,
        dotColor = dotColor
    )
}

/** Filtro secundario (sin estado "on" ni lógica en el MVP): outline de 32dp. */
@Composable
fun SecondaryFilterChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Painter? = null
) {
    val colors = ParcheUTheme.colors
    OutlinedChipFrame(
        selected = false,
        enabled = enabled,
        modifier = modifier.touchTarget().clickable(
            enabled = enabled,
            role = Role.Button,
            onClick = onClick
        ),
        label = label,
        icon = icon,
        iconSize = 16.dp,
        selectedContainer = colors.prc,
        selectedContent = colors.onprc
    )
}

/** Chip de interés (F2): píldora outline de 32dp, icono de 16. Seleccionable (casilla). */
@Composable
fun InterestChip(
    label: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Painter? = null
) {
    val colors = ParcheUTheme.colors
    OutlinedChipFrame(
        selected = selected,
        enabled = enabled,
        modifier =
        modifier
            .touchTarget()
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onSelectedChange
            ),
        label = label,
        icon = icon,
        iconSize = 16.dp,
        selectedContainer = colors.prc,
        selectedContent = colors.onprc
    )
}

@Composable
private fun OutlinedChipFrame(
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    label: String,
    icon: Painter?,
    iconSize: Dp,
    selectedContainer: Color,
    selectedContent: Color,
    dotColor: Color? = null
) {
    val colors = ParcheUTheme.colors
    // Dirección A: los chips de filtro son píldoras (antes radio 12).
    val shape = ParcheUTheme.shapes.pill
    val container = if (selected) selectedContainer else Color.Transparent
    val rawContent = if (selected) selectedContent else colors.tx
    val content = if (enabled) rawContent else rawContent.disabled()
    val borderColor = if (enabled) colors.ln else colors.ln.copy(alpha = 0.5f)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Row(
            modifier =
            Modifier
                .height(ParcheUSizes.FilterChip)
                .clip(shape)
                .background(if (enabled) container else container.disabled())
                .then(
                    if (selected) {
                        Modifier
                    } else {
                        Modifier.border(
                            BorderStroke(1.dp, borderColor),
                            shape
                        )
                    }
                )
                .padding(PaddingValues(horizontal = 12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dotColor != null) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(ParcheUTheme.shapes.pill)
                        .background(if (enabled) dotColor else dotColor.disabled())
                )
                Spacer(Modifier.width(6.dp))
            }
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(iconSize)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

enum class PillChipVariant { Neutral, Affinity }

/** Píldora informativa (lo que el gemelo aprende). Neutra: s2 12/600. Afinidad: prc/onprc 13/600. No es interactiva. */
@Composable
fun PillChip(
    text: String,
    modifier: Modifier = Modifier,
    variant: PillChipVariant = PillChipVariant.Neutral,
    icon: Painter? = null
) {
    val colors = ParcheUTheme.colors
    val (container, content, size) =
        when (variant) {
            PillChipVariant.Neutral -> Triple(colors.s2, colors.tx, 12)
            PillChipVariant.Affinity -> Triple(colors.prc, colors.onprc, 13)
        }
    Row(
        modifier =
        modifier
            .clip(ParcheUTheme.shapes.pill)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = size.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = content
        )
    }
}
