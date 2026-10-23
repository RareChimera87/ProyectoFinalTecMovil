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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.designsystem.theme.ParcheURadius
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Variantes de tarjeta del inventario: estándar (radio 20, padding 16), estadística (16, 12x14), sugerencia (padding 14) y plan activo (radio 24, borde degradado). */
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
