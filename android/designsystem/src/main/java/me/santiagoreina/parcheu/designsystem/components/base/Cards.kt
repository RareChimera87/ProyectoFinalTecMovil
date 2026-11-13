package me.santiagoreina.parcheu.designsystem.components.base

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.designsystem.theme.ModeColors
import me.santiagoreina.parcheu.designsystem.theme.ParcheURadius
import me.santiagoreina.parcheu.designsystem.theme.ParcheUSizes
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Variantes de tarjeta del inventario: estándar (radio 18 desde la dirección A, padding 16), estadística (16, 12x14), sugerencia (padding 14) y plan activo (radio 24, borde degradado). */
enum class ParcheUCardVariant(val radius: Dp, val padding: PaddingValues) {
    Standard(ParcheURadius.Card, PaddingValues(16.dp)),
    Stat(ParcheURadius.FieldLarge, PaddingValues(horizontal = 14.dp, vertical = 12.dp)),
    Suggestion(ParcheURadius.Card, PaddingValues(14.dp)),
    ActivePlan(ParcheURadius.Hero, PaddingValues(16.dp))
}

/** Tarjeta de superficie s1. Con [onClick] es un botón (área táctil de al menos 48dp); sin él es estática. */
@Composable
fun ParcheUCard(
    modifier: Modifier = Modifier,
    variant: ParcheUCardVariant = ParcheUCardVariant.Standard,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = ParcheUTheme.colors
    val shape = RoundedCornerShape(variant.radius)
    Column(
        modifier =
        modifier
            .clip(shape)
            .background(colors.s1, shape)
            .then(
                if (variant == ParcheUCardVariant.ActivePlan) {
                    Modifier.border(BorderStroke(1.5.dp, colors.plusGradient.brush()), shape)
                } else {
                    Modifier
                }
            )
            .then(
                if (onClick != null) {
                    Modifier.touchTarget().clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(variant.padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/**
 * Tarjeta de "estado" de la dirección A "Parche Vivo": superficie s1, radio 18 y un lomo de 5 dp a la
 * izquierda con el color pleno del modo ([mode]`.solid`). Úsala para todo lo que pertenece a una relación
 * de un modo concreto (simulación en curso, resultado listo, sugerencia, chat): el color del modo organiza
 * la pantalla en vez de vivir solo en un chip. El lomo es decorativo: el modo debe decirse también en
 * texto (p. ej. "· Estudio" o un `ModeChip`).
 *
 * Con [onClick] es un botón (área táctil ≥ 48 dp) y [onClickLabel] se lee en TalkBack.
 */
@Composable
fun ModeSpineCard(
    mode: ModeColors,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ModeSpineCardDefaults.ContentPadding,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = ParcheUTheme.colors
    val shape = RoundedCornerShape(ParcheURadius.Card)
    val spine = ParcheUSizes.ModeSpine
    Column(
        modifier =
        modifier
            .clip(shape)
            .background(colors.s1, shape)
            .drawBehind { drawRect(mode.solid, size = Size(spine.toPx(), size.height)) }
            .then(
                if (onClick != null) {
                    Modifier.touchTarget().clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = onClickLabel,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(start = spine)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

object ModeSpineCardDefaults {
    /** 12 a la izquierda del lomo, 14 a la derecha y 12 arriba y abajo (mockup A · Inicio). */
    val ContentPadding = PaddingValues(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
}

/**
 * Encabezado de sección: 12/600 en mayúsculas con espaciado .6 en mu y, opcionalmente, una acción de
 * texto 13/600 en prt.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    val colors = ParcheUTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title.uppercase(),
            style = ParcheUTheme.textStyles.overline,
            color = colors.mu,
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

/** Número grande y rótulo para tarjetas estadísticas (número Sora 20/700). */
@Composable
fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    ParcheUCard(modifier = modifier, variant = ParcheUCardVariant.Stat) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            ),
            color = colors.tx
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = colors.mu)
    }
}
