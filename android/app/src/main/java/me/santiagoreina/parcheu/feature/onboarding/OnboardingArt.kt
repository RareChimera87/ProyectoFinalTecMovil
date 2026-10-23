package me.santiagoreina.parcheu.feature.onboarding

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.designsystem.theme.ParcheUPalette
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.designsystem.theme.SoraFontFamily

/*
 * Arte de A1-A4 dibujado con los tokens. Wordmark, AppIcon y la mascota del gemelo tendrán su versión
 * oficial en :designsystem con E1.2 y E1.5; cuando se mezclen, estas versiones locales se reemplazan.
 */

/** Wordmark "ParcheU": Sora 700, la U en naranja. */
@Composable
internal fun OnboardingWordmark(size: Dp, modifier: Modifier = Modifier) {
    val text =
        buildAnnotatedString {
            withStyle(SpanStyle(color = ParcheUTheme.colors.wordmark)) { append("Parche") }
            withStyle(SpanStyle(color = ParcheUPalette.Orange)) { append("U") }
        }
    Text(
        text = text,
        modifier = modifier.semantics { contentDescription = "ParcheU" },
        style =
        TextStyle(
            fontFamily = SoraFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = size.value.sp,
            lineHeight = size.value.sp,
            letterSpacing = (-size.value * 0.03f).sp
        )
    )
}

/** Ícono de la app (96 x 96, radio 26): polilínea en U con 5 nodos, el inferior naranja. */
@Composable
internal fun OnboardingAppIcon(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val k = this.size.width / 96f
        drawRoundRect(ParcheUPalette.Violet, cornerRadius = CornerRadius(26f * k))
        val points = listOf(30f to 24f, 30f to 52f, 48f to 70f, 66f to 52f, 66f to 24f).map {
            Offset(it.first * k, it.second * k)
        }
        for (i in 0 until points.lastIndex) {
            drawLine(
                Color.White.copy(alpha = 0.7f),
                points[i],
                points[i + 1],
                strokeWidth = 4f * k,
                cap = StrokeCap.Round
            )
        }
        val radii = listOf(7f, 6f, 8f, 6f, 7f)
        points.forEachIndexed { i, p ->
            drawCircle(
                if (i ==
                    2
                ) {
                    ParcheUPalette.Orange
                } else {
                    Color.White
                },
                radius = radii[i] * k,
                center = p
            )
        }
    }
}

/** Mascota del gemelo: antena y cuerpo de blob (alto = 1,125 x ancho) con ojos y boca en tinta. */
@Composable
internal fun OnboardingMascot(width: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width, width * 1.125f).clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val bodyH = h * 0.825f
        val bodyTop = h - bodyH
        // Antena: palito y bolita.
        drawRoundRect(
            color,
            topLeft = Offset(w / 2 - w * 0.04f, h * 0.053f),
            size = Size(w * 0.08f, w * 0.16f),
            cornerRadius = CornerRadius(w * 0.04f)
        )
        drawCircle(color, radius = w * 0.08f, center = Offset(w / 2, w * 0.08f))
        // Cuerpo: elipse algo achatada abajo (aproxima el border-radius 48/52/44/46 del mockup).
        drawRoundRect(
            color,
            topLeft = Offset(0f, bodyTop),
            size = Size(w, bodyH),
            cornerRadius = CornerRadius(w * 0.5f, bodyH * 0.55f)
        )
        val ink = ParcheUPalette.Ink
        val eyeW = w * 0.13f
        val eyeH = w * 0.17f
        val eyeY = bodyTop + bodyH * 0.36f
        drawOval(ink, topLeft = Offset(w * 0.27f, eyeY), size = Size(eyeW, eyeH))
        drawOval(ink, topLeft = Offset(w * 0.73f - eyeW, eyeY), size = Size(eyeW, eyeH))
        val mouthW = w * 0.18f
        val mouthY = bodyTop + bodyH * 0.63f
        drawArc(
            ink.copy(alpha = 0.85f),
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(w / 2 - mouthW / 2, mouthY - mouthW * 0.39f),
            size = Size(mouthW, mouthW * 0.78f)
        )
    }
}

/** Avatar con iniciales sobre violeta. */
@Composable
internal fun InitialsAvatar(initials: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).background(ParcheUPalette.Violet, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            style = TextStyle(
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = (
                    size.value *
                        0.375f
                    ).sp
            )
        )
    }
}

/** Ilustración de A4: un ego-grafo pequeño con anillos punteados y nodos por modo. */
@Composable
internal fun OnboardingGraph(size: Dp, modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val k = this.size.width / 328f
        val dash = PathEffect.dashPathEffect(floatArrayOf(2f * k * 2, 5f * k * 2))
        listOf(62f, 102f, 140f).forEach {
            drawCircle(
                colors.ln,
                radius = it * k,
                center = c,
                style = Stroke(width = 1.5f * k, pathEffect = dash)
            )
        }
        val nodes =
            listOf(
                Triple(-40f, 62f, ParcheUPalette.Mint),
                Triple(70f, 62f, ParcheUPalette.Violet),
                Triple(160f, 102f, ParcheUPalette.Coral),
                Triple(230f, 102f, ParcheUPalette.ElectricBlue),
                Triple(15f, 140f, ParcheUPalette.ElectricBlue),
                Triple(120f, 140f, ParcheUPalette.Mint),
                Triple(285f, 140f, ParcheUPalette.Violet)
            )
        nodes.forEachIndexed { i, (deg, r, color) ->
            val rad = Math.toRadians(deg.toDouble())
            val p = Offset(
                c.x + (r * k * kotlin.math.cos(rad)).toFloat(),
                c.y + (r * k * kotlin.math.sin(rad)).toFloat()
            )
            val solid = i < 4
            drawLine(
                color.copy(alpha = if (solid) 0.95f else 0.3f),
                c,
                p,
                strokeWidth = (if (solid) 2.5f else 1.5f) * k * 2,
                pathEffect = if (i in
                    2..3
                ) {
                    PathEffect.dashPathEffect(floatArrayOf(10f * k, 10f * k))
                } else {
                    null
                }
            )
            drawCircle(color, radius = (if (r < 100f) 20f else 16f) * k, center = p)
        }
        drawCircle(ParcheUPalette.Violet, radius = 28f * k, center = c)
        drawCircle(
            Color.White,
            radius = 28f * k,
            center = c,
            style = Stroke(width = 3f * k, join = StrokeJoin.Round)
        )
    }
}

/** Indicador de página de A2-A4: la activa mide 22 x 8, las demás 8 x 8 en `ln`. */
@Composable
internal fun PageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    Row(
        modifier = modifier.semantics { contentDescription = "Paso ${current + 1} de $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(width = if (i == current) 22.dp else 8.dp, height = 8.dp)
                    .background(
                        if (i ==
                            current
                        ) {
                            colors.primary
                        } else {
                            colors.ln
                        },
                        RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}

/** Tres puntos de 8dp que laten (A1 y estados de carga). [animate] = false para capturas estables. */
@Composable
internal fun LoadingDots(modifier: Modifier = Modifier, animate: Boolean = true) {
    val phase =
        if (animate) {
            val transition = rememberInfiniteTransition(label = "dots")
            val value by transition.animateFloat(
                initialValue = 0f,
                targetValue = 3f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
                label = "phase"
            )
            value
        } else {
            0f
        }
    Row(
        modifier = modifier.semantics { contentDescription = "Cargando" },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(3) { i ->
            val distance = ((i - phase) % 3 + 3) % 3
            val alpha = if (animate) 1f - distance * 0.35f else listOf(1f, 0.6f, 0.3f)[i]
            Box(
                Modifier
                    .width(8.dp)
                    .size(8.dp)
                    .alpha(alpha)
                    .background(ParcheUPalette.Violet, CircleShape)
            )
        }
    }
}
