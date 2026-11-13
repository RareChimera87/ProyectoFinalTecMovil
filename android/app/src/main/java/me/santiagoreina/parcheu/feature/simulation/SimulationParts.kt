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
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotGlyph
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
 * Gemelo de las escenas F4-F6, F8 e intro: "La P que mira" (`TwinMascotGlyph`) del [color] dado, alto
 * 1,125 x ancho. Con [error] la pasa a gris y baja la opacidad a .5 (inventario §3). Con [mirrored] mira
 * hacia la izquierda (la otra persona frente a ti).
 */
@Composable
internal fun SimulationMascot(
    color: Color,
    width: Dp,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    mirrored: Boolean = false
) {
    TwinMascotGlyph(
        width = width,
        color = if (error) grayscale(color) else color,
        mirrored = mirrored,
        modifier = modifier.alpha(if (error) 0.5f else 1f)
    )
}

private fun grayscale(color: Color): Color {
    val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
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
                        RepeatMode.Reverse
                    ),
                    label = "dot$index"
                )
            Box(
                Modifier.size(
                    6.dp
                ).clip(CircleShape).background(color).alpha(if (animate) alpha else 0.7f)
            )
        }
    }
}
