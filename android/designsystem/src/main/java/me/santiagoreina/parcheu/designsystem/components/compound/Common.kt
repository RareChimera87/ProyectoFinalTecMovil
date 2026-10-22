package me.santiagoreina.parcheu.designsystem.components.compound

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * Borde discontinuo con esquinas redondeadas (LockedFeatureBanner y avatar anónimo). Compose no trae un
 * `border` con guiones, así que se dibuja un trazo con [PathEffect.dashPathEffect] dentro del área.
 */
internal fun Modifier.dashedBorder(
    color: Color,
    width: Dp,
    radius: Dp,
    dash: Dp = width * 4,
    gap: Dp = width * 3,
): Modifier = drawBehind {
    val strokeWidth = width.toPx()
    val inset = strokeWidth / 2f
    val corner = (radius.toPx() - inset).coerceAtLeast(0f)
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(
            size.width - strokeWidth,
            size.height - strokeWidth,
        ),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx())),
        ),
    )
}
