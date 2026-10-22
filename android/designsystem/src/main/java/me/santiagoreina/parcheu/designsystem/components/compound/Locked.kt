package me.santiagoreina.parcheu.designsystem.components.compound

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUPlusBadge
import me.santiagoreina.parcheu.designsystem.components.base.TonalButton
import me.santiagoreina.parcheu.designsystem.components.base.touchTarget
import me.santiagoreina.parcheu.designsystem.theme.ParcheURadius
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Etiqueta de prueba del placeholder borroso (API >= 31). */
const val LOCKED_OVERLAY_BLUR_TAG = "locked_overlay_blur"

/** Etiqueta de prueba del scrim opaco (API < 31). */
const val LOCKED_OVERLAY_SCRIM_TAG = "locked_overlay_scrim"

/** Desde esta API `Modifier.blur` funciona; antes es un no-op y se usa un scrim opaco (docs/arquitectura.md, riesgo 6). */
private const val BLUR_MIN_SDK = Build.VERSION_CODES.S

/** Forma decorativa que se dibuja detrás del bloqueo. No lleva datos: son rectángulos y círculos fijos. */
enum class LockedPlaceholder { List, Graph, Ring, Chart }

/**
 * Contenido premium oculto (D7, F7). **No recibe datos reales**: solo un [LockedPlaceholder] decorativo, así
 * que lo que se ve borroso nunca es información del servidor (el servidor tampoco la envía). En API >= 31
 * el placeholder se desenfoca (blur 6dp, opacidad .55); en API < 31 un scrim opaco lo cubre del todo.
 * Muestra un círculo de 44dp con candado, el [title] 15/600 y el botón tonal [actionLabel].
 */
@Composable
fun LockedOverlay(
    title: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    lockIcon: Painter,
    modifier: Modifier = Modifier,
    placeholder: LockedPlaceholder = LockedPlaceholder.List,
) {
    val colors = ParcheUTheme.colors
    val shape = ParcheUTheme.shapes.card
    val blurSupported = Build.VERSION.SDK_INT >= BLUR_MIN_SDK
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 220.dp)
            .clip(shape)
            .background(colors.s1, shape),
    ) {
        if (blurSupported) {
            Box(
                Modifier
                    .matchParentSize()
                    .alpha(0.55f)
                    .blur(6.dp)
                    // Decorativo: ni el lector de pantalla ni las pruebas deben ver "contenido".
                    .clearAndSetSemantics { testTag = LOCKED_OVERLAY_BLUR_TAG },
            ) { PlaceholderArt(placeholder) }
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(colors.s1)
                    .clearAndSetSemantics { testTag = LOCKED_OVERLAY_SCRIM_TAG },
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(colors.prc, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = lockIcon,
                    contentDescription = null,
                    tint = colors.onprc,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.tx,
            )
            TonalButton(
                text = actionLabel,
                onClick = onActionClick,
                size = ParcheUButtonSize.Medium,
            )
        }
    }
}

@Composable
private fun PlaceholderArt(kind: LockedPlaceholder) {
    val colors = ParcheUTheme.colors
    val fill = colors.s2
    val accent = colors.prc
    Canvas(Modifier.fillMaxSize()) {
        when (kind) {
            LockedPlaceholder.List -> drawListArt(fill, accent)
            LockedPlaceholder.Graph -> drawGraphArt(fill, accent, colors.ln)
            LockedPlaceholder.Ring -> drawRingArt(fill, accent)
            LockedPlaceholder.Chart -> drawChartArt(fill, accent)
        }
    }
}

private fun DrawScope.drawListArt(fill: Color, accent: Color) {
    val row = 52.dp.toPx()
    val pad = 16.dp.toPx()
    var y = pad
    while (y + row <= size.height) {
        drawCircle(accent, radius = 16.dp.toPx(), center = Offset(pad + 16.dp.toPx(), y + row / 2))
        drawRoundRect(
            fill,
            topLeft = Offset(pad + 48.dp.toPx(), y + 10.dp.toPx()),
            size = Size(size.width * 0.5f, 10.dp.toPx()),
            cornerRadius = CornerRadius(5.dp.toPx()),
        )
        drawRoundRect(
            fill,
            topLeft = Offset(pad + 48.dp.toPx(), y + 28.dp.toPx()),
            size = Size(size.width * 0.32f, 8.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
        y += row
    }
}

private fun DrawScope.drawGraphArt(fill: Color, accent: Color, line: Color) {
    val center = Offset(size.width / 2, size.height / 2)
    val base = size.minDimension / 2
    listOf(0.35f, 0.65f, 0.95f).forEach {
        drawCircle(line, radius = base * it, center = center, style = Stroke(1.5.dp.toPx()))
    }
    drawCircle(accent, radius = 14.dp.toPx(), center = center)
    listOf(20f, 95f, 170f, 245f, 310f).forEachIndexed { i, degrees ->
        val r = base * if (i % 2 == 0) 0.65f else 0.95f
        val radians = Math.toRadians(degrees.toDouble())
        val p = Offset(
            center.x + (r * kotlin.math.cos(radians)).toFloat(),
            center.y + (r * kotlin.math.sin(radians)).toFloat(),
        )
        drawLine(line, center, p, strokeWidth = 1.5.dp.toPx())
        drawCircle(fill, radius = 10.dp.toPx(), center = p)
    }
}

private fun DrawScope.drawRingArt(fill: Color, accent: Color) {
    val diameter = size.minDimension * 0.55f
    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
    drawArc(
        fill,
        0f,
        360f,
        useCenter = false,
        topLeft = topLeft,
        size = Size(diameter, diameter),
        style = Stroke(11.dp.toPx()),
    )
    drawArc(
        accent,
        -90f,
        250f,
        useCenter = false,
        topLeft = topLeft,
        size = Size(diameter, diameter),
        style = Stroke(11.dp.toPx(), cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawChartArt(fill: Color, accent: Color) {
    val pad = 20.dp.toPx()
    val w = size.width - pad * 2
    val h = size.height - pad * 2
    val ys = listOf(0.8f, 0.6f, 0.65f, 0.4f, 0.3f, 0.15f)
    val path = Path()
    ys.forEachIndexed { i, y ->
        val x = pad + w * i / (ys.size - 1)
        val py = pad + h * y
        if (i == 0) path.moveTo(x, py) else path.lineTo(x, py)
    }
    drawRoundRect(
        fill,
        Offset(pad, pad),
        Size(w, h),
        CornerRadius(12.dp.toPx()),
    )
    drawPath(path, accent, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
}

/**
 * Banner de función bloqueada que invita sin ocultar (D1, I2): borde discontinuo ln de radio 16, candado,
 * texto y la insignia ParcheU+. Con [onClick] es un botón.
 */
@Composable
fun LockedFeatureBanner(text: String, lockIcon: Painter, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val colors = ParcheUTheme.colors
    val shape = RoundedCornerShape(ParcheURadius.FieldLarge)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .dashedBorder(colors.ln, 1.5.dp, ParcheURadius.FieldLarge)
            .then(
                if (onClick != null) {
                    Modifier.touchTarget().clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = lockIcon,
            contentDescription = null,
            tint = colors.mu,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = colors.tx,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        ParcheUPlusBadge()
    }
}
