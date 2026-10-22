package me.santiagoreina.parcheu.designsystem.mascot

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Proporción alto / ancho de la mascota (docs/mockups-inventario.md, "Dimensión de mascota"). */
const val MASCOT_ASPECT_RATIO = 1.125f

/** Tamaños oficiales de la mascota: ancho en dp. El alto siempre es 1,125 × el ancho. */
enum class TwinMascotSize(val width: Dp) {
    S(30.dp),
    M(56.dp),
    L(80.dp),
    XL(116.dp);

    val height: Dp get() = width * MASCOT_ASPECT_RATIO
}

/** Color de la mascota por rol: violeta para el usuario; el color del modo para el otro. */
enum class TwinMascotTone { User, Friendship, Couple, Study }

/**
 * Gemelo digital dibujado en Canvas: blob, antena, ojos y boca. Con [talking] mueve la antena y abre y
 * cierra la boca; si el sistema tiene la escala de animaciones en 0 (o [animationsEnabled] es falso)
 * se dibuja quieta. En [error] se muestra con opacidad .5 y en escala de grises.
 */
@Composable
fun TwinMascot(
    modifier: Modifier = Modifier,
    size: TwinMascotSize = TwinMascotSize.M,
    tone: TwinMascotTone = TwinMascotTone.User,
    error: Boolean = false,
    talking: Boolean = false,
    animationsEnabled: Boolean = rememberAnimationsEnabled(),
    contentDescription: String? = null
) {
    val colors = ParcheUTheme.colors
    val base =
        when (tone) {
            TwinMascotTone.User -> colors.primary
            TwinMascotTone.Friendship -> colors.friendship.solid
            TwinMascotTone.Couple -> colors.couple.solid
            TwinMascotTone.Study -> colors.study.solid
        }
    val body = if (error) base.toGray() else base
    val features = colors.onMode.copy(alpha = FEATURE_ALPHA)

    val phase =
        if (talking && animationsEnabled) {
            rememberInfiniteTransition(label = "mascotTalking")
                .animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(tween(TALK_PERIOD_MS, easing = LinearEasing), RepeatMode.Reverse),
                    label = "mascotPhase"
                )
                .value
        } else {
            0f
        }

    val semanticsModifier =
        if (contentDescription != null) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier.clearAndSetSemantics {}
        }

    Canvas(
        modifier
            .size(size.width, size.height)
            .then(if (error) Modifier.alpha(ERROR_ALPHA) else Modifier)
            .then(semanticsModifier)
    ) {
        drawMascot(body, features, phase)
    }
}

/** Lee `Settings.Global.ANIMATOR_DURATION_SCALE`: con 0 el usuario pidió no ver animaciones. */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
            .getOrDefault(true)
    }
}

private fun Color.toGray(): Color {
    val l = luminance()
    return Color(l, l, l, alpha)
}

private fun DrawScope.drawMascot(body: Color, features: Color, phase: Float) {
    val w = size.width
    val h = size.height
    // Cuerpo: el blob ocupa el área bajo la antena.
    val top = h * ANTENNA_AREA
    val bodyH = h - top
    drawPath(blobPath(w, bodyH, top), body)

    // Antena: tallo y bola; en 'conversando' se balancea ±12° desde la base.
    val baseX = w * 0.5f
    val sway = (phase - 0.5f) * 2f * ANTENNA_SWAY_DEGREES
    rotate(sway, pivot = Offset(baseX, top)) {
        val tipY = h * 0.03f
        drawLine(features, Offset(baseX, top), Offset(baseX, tipY + w * 0.05f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)
        drawCircle(body, radius = w * 0.07f, center = Offset(baseX, tipY + w * 0.05f))
        drawCircle(features, radius = w * 0.07f, center = Offset(baseX, tipY + w * 0.05f), style = Stroke(w * 0.03f))
    }

    // Ojos.
    val eyeR = w * 0.07f
    val eyeY = top + bodyH * 0.40f
    drawCircle(features, eyeR, Offset(w * 0.34f, eyeY))
    drawCircle(features, eyeR, Offset(w * 0.66f, eyeY))

    // Boca: sonrisa cuando está quieta; óvalo que se abre y cierra al conversar.
    val mouthY = top + bodyH * 0.66f
    val mouthW = w * 0.28f
    if (phase == 0f) {
        drawArc(
            features,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.5f - mouthW / 2f, mouthY - mouthW * 0.35f),
            size = Size(mouthW, mouthW * 0.7f),
            style = Stroke(w * 0.045f, cap = StrokeCap.Round)
        )
    } else {
        val open = w * (0.03f + 0.09f * phase)
        drawOval(features, Offset(w * 0.5f - mouthW / 2f, mouthY - open / 2f), Size(mouthW, open))
    }
}

/** Blob con radios elípticos "48% 52% 44% 46% / 56% 54% 46% 44%" del inventario. */
private fun blobPath(w: Float, h: Float, yOffset: Float): Path {
    val tlx = w * 0.48f
    val trx = w * 0.52f
    val brx = w * 0.44f
    val blx = w * 0.46f
    val tly = h * 0.56f
    val try_ = h * 0.54f
    val bry = h * 0.46f
    val bly = h * 0.44f
    return Path().apply {
        moveTo(tlx, yOffset)
        lineTo(w - trx, yOffset)
        arcTo(Rect(w - 2 * trx, yOffset, w, yOffset + 2 * try_), -90f, 90f, false)
        lineTo(w, yOffset + h - bry)
        arcTo(Rect(w - 2 * brx, yOffset + h - 2 * bry, w, yOffset + h), 0f, 90f, false)
        lineTo(blx, yOffset + h)
        arcTo(Rect(0f, yOffset + h - 2 * bly, 2 * blx, yOffset + h), 90f, 90f, false)
        lineTo(0f, yOffset + tly)
        arcTo(Rect(0f, yOffset, 2 * tlx, yOffset + 2 * tly), 180f, 90f, false)
        close()
    }
}

private const val FEATURE_ALPHA = 0.85f
private const val ERROR_ALPHA = 0.5f
private const val ANTENNA_AREA = 0.14f
private const val ANTENNA_SWAY_DEGREES = 12f
private const val TALK_PERIOD_MS = 420
