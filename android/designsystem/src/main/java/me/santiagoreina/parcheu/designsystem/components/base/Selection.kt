package me.santiagoreina.parcheu.designsystem.components.base

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.theme.ParcheUSizes
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/**
 * Interruptor 52 x 32 radio 16. ON: violeta con thumb blanco de 24 y check. OFF: s2, borde 2 mu y thumb de 16.
 * [contentDescription] es obligatorio porque el interruptor no lleva texto propio; el estado On/Off lo
 * expone la semántica de `toggleable`.
 */
@Composable
fun ParcheUSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = ParcheUTheme.colors
    val thumbSize by animateDpAsState(if (checked) 24.dp else 16.dp, label = "thumbSize")
    val thumbOffset by animateDpAsState(if (checked) 24.dp else 8.dp, label = "thumbOffset")
    val track = if (checked) colors.primary else colors.s2
    val thumbBase = if (checked) colors.white else colors.mu
    val thumb = if (enabled) thumbBase else thumbBase.disabled()
    Box(
        modifier =
        modifier
            .touchTarget()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier =
            Modifier
                .size(ParcheUSizes.SwitchWidth, ParcheUSizes.SwitchHeight)
                .clip(CircleShape)
                .background(if (enabled) track else track.disabled())
                .then(
                    if (checked) {
                        Modifier
                    } else {
                        Modifier.border(
                            2.dp,
                            if (enabled) colors.mu else colors.mu.disabled(),
                            CircleShape
                        )
                    }
                )
        ) {
            Box(
                modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = thumbOffset)
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(thumb),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Canvas(Modifier.size(14.dp)) {
                        drawCheck(
                            if (enabled) colors.primary else colors.primary.disabled(),
                            2.dp.toPx()
                        )
                    }
                }
            }
        }
    }
}

/** Casilla de 22 x 22 radio 7: marcada violeta con check blanco; desmarcada con borde 2 mu. */
@Composable
fun ParcheUCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = ParcheUTheme.colors
    val shape = ParcheUTheme.shapes.checkbox
    Box(
        modifier =
        modifier
            .touchTarget()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier =
            Modifier
                .size(22.dp)
                .clip(shape)
                .then(
                    if (checked) {
                        Modifier.background(
                            if (enabled) colors.primary else colors.primary.disabled()
                        )
                    } else {
                        Modifier.border(
                            2.dp,
                            if (enabled) colors.mu else colors.mu.disabled(),
                            shape
                        )
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Canvas(Modifier.size(14.dp)) {
                    drawCheck(if (enabled) colors.white else colors.white.disabled(), 2.dp.toPx())
                }
            }
        }
    }
}

/** Fila con radio: círculo de 22 con borde 2 (violeta si está elegido, mu si no) y punto de 10. */
@Composable
fun ParcheURadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true
) {
    val colors = ParcheUTheme.colors
    val ring = if (selected) colors.primary else colors.mu
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .touchTarget()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier.size(
                22.dp
            ).border(2.dp, if (enabled) ring else ring.disabled(), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    Modifier.size(
                        10.dp
                    ).clip(
                        CircleShape
                    ).background(if (enabled) colors.primary else colors.primary.disabled())
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) colors.tx else colors.tx.disabled()
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) colors.mu else colors.mu.disabled()
                )
            }
        }
    }
}

private fun DrawScope.drawCheck(color: Color, strokeWidth: Float) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val path =
        androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.14f, h * 0.52f)
            lineTo(w * 0.40f, h * 0.76f)
            lineTo(w * 0.86f, h * 0.26f)
        }
    drawPath(path, color, style = stroke)
}
