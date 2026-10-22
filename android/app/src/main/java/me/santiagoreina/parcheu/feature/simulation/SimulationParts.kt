package me.santiagoreina.parcheu.feature.simulation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.designsystem.theme.ModeColors
import me.santiagoreina.parcheu.designsystem.theme.ParcheUColors

/** Nombre del modo en español para el encabezado de F4 y las filas de la lista. */
internal fun Mode.label(): String = when (this) {
    Mode.AMISTAD -> "Amistad"
    Mode.PAREJA -> "Pareja"
    Mode.ESTUDIO -> "Estudio"
}

internal fun ParcheUColors.forMode(mode: Mode): ModeColors = when (mode) {
    Mode.AMISTAD -> friendship
    Mode.PAREJA -> couple
    Mode.ESTUDIO -> study
}

/**
 * Gemelo de las escenas F4-F6: blob con antena, ojos y boca #14111F, alto 1,125 x ancho. Con [error] baja la
 * opacidad a .5 y apaga la saturación (inventario §3). Es la versión local de F4-F6 mientras el
 * `TwinMascot` de E1.5 no está en `dev`.
 */
@Composable
internal fun SimulationMascot(color: Color, width: Dp, modifier: Modifier = Modifier, error: Boolean = false) {
    val ink = Color(0xFF14111F)
    val tint = if (error) grayscale(color) else color
    val height = width * 1.125f
    Box(modifier.size(width, height).alpha(if (error) 0.5f else 1f)) {
        Box(
            Modifier.align(Alignment.TopCenter)
                .offset(y = height * 0.054f)
                .size(width * 0.08f, height * 0.142f)
                .clip(RoundedCornerShape(50))
                .background(tint),
        )
        Box(
            Modifier.align(Alignment.BottomCenter)
                .size(width, height * 0.89f)
                .clip(
                    RoundedCornerShape(
                        topStartPercent = 48,
                        topEndPercent = 52,
                        bottomEndPercent = 44,
                        bottomStartPercent = 46,
                    ),
                )
                .background(tint),
        )
        Row(
            Modifier.align(Alignment.Center).offset(y = -height * 0.04f),
            horizontalArrangement = Arrangement.spacedBy(width * 0.2f),
        ) {
            repeat(2) {
                Box(
                    Modifier.size(
                        width * 0.1f,
                        height * 0.12f,
                    ).clip(CircleShape).background(ink.copy(alpha = .85f)),
                )
            }
        }
        Box(
            Modifier.align(Alignment.Center)
                .offset(y = height * 0.17f)
                .width(width * 0.22f)
                .size(width * 0.22f, height * 0.05f)
                .clip(RoundedCornerShape(50))
                .background(ink.copy(alpha = .85f)),
        )
    }
}

private fun grayscale(color: Color): Color {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return Color(luminance, luminance, luminance, color.alpha)
}

/** Tres puntos que laten (TypingIndicator). Con [animate] en falso quedan quietos (capturas). */
@Composable
internal fun TypingDots(color: Color, modifier: Modifier = Modifier, animate: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val alpha by
                transition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec =
                    infiniteRepeatable(
                        tween(600, delayMillis = index * 150, easing = LinearEasing),
                        RepeatMode.Reverse,
                    ),
                    label = "dot$index",
                )
            Box(
                Modifier.size(
                    6.dp,
                ).clip(CircleShape).background(color).alpha(if (animate) alpha else 0.7f),
            )
        }
    }
}
