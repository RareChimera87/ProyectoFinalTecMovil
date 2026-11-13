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
import me.santiagoreina.parcheu.designsystem.brand.ParcheUAppIcon
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotGlyph
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

/** Ícono de la app: el `ParcheUAppIcon` de :designsystem ("La P que mira" blanca sobre violeta). */
@Composable
internal fun OnboardingAppIcon(size: Dp, modifier: Modifier = Modifier) {
    ParcheUAppIcon(size = size, modifier = modifier)
}

/** Mascota del gemelo: "La P que mira" (`TwinMascotGlyph`) del [color] dado, alto = 1,125 x ancho. */
@Composable
internal fun OnboardingMascot(width: Dp, color: Color, modifier: Modifier = Modifier) {
    TwinMascotGlyph(width = width, color = color, modifier = modifier)
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
