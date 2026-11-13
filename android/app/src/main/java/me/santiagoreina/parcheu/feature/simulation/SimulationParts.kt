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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.designsystem.mascot.MascotMouth
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotGlyph
import me.santiagoreina.parcheu.designsystem.mascot.rememberAnimationsEnabled
import me.santiagoreina.parcheu.designsystem.theme.ModeColors
import me.santiagoreina.parcheu.designsystem.theme.ParcheUColors
import me.santiagoreina.parcheu.designsystem.theme.ParcheUPalette
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

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
 *
 * Con [talking] abre la boca; si además [animate] y el sistema permite animaciones, la boca abre y cierra
 * y la P se balancea unos grados sobre el pie (la que habla). Con "reducir animaciones" queda quieta con
 * la boca abierta.
 */
@Composable
internal fun SimulationMascot(
    color: Color,
    width: Dp,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    mirrored: Boolean = false,
    talking: Boolean = false,
    animate: Boolean = true,
    eyeColor: Color = ParcheUPalette.Ink
) {
    val systemAllows = rememberAnimationsEnabled()
    val moving = talking && animate && systemAllows && !error
    val phase =
        if (moving) {
            val transition = rememberInfiniteTransition(label = "simTalk")
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                infiniteRepeatable(
                    tween(TALK_PERIOD_MS, easing = LinearEasing),
                    RepeatMode.Reverse
                ),
                label = "simTalkPhase"
            ).value
        } else {
            0.5f
        }
    val mouth =
        when {
            !talking -> MascotMouth.Quiet
            moving && phase < MOUTH_CLOSED_BELOW -> MascotMouth.Quiet
            else -> MascotMouth.Talking
        }
    val sway = (phase - 0.5f) * 2f * SWAY_DEGREES * if (mirrored) -1f else 1f
    TwinMascotGlyph(
        width = width,
        color = if (error) grayscale(color) else color,
        eyeColor = eyeColor,
        mouth = mouth,
        mirrored = mirrored,
        modifier =
        modifier
            .graphicsLayer {
                rotationZ = sway
                // Pivote en el pie del tallo (22, 86 de la caja 80 × 90), espejado si mira a la izquierda.
                transformOrigin = TransformOrigin(if (mirrored) 1f - FOOT_X else FOOT_X, FOOT_Y)
            }
            .alpha(if (error) 0.5f else 1f)
    )
}

/** Quién habla en una escena de dos gemelos. */
internal enum class TwinSpeaker { Me, Other, Nobody }

/**
 * Las dos P frente a frente: la tuya en violeta mirando a la derecha y la de la otra persona espejada,
 * del color de su modo ([otherColor]), mirando hacia ti. Anima solo la que habla ([speaker]).
 * Es decorativa: quien la usa describe la escena.
 */
@Composable
internal fun FacingTwins(
    otherColor: Color,
    width: Dp,
    modifier: Modifier = Modifier,
    speaker: TwinSpeaker = TwinSpeaker.Nobody,
    gap: Dp = 20.dp,
    error: Boolean = false,
    animate: Boolean = true,
    myColor: Color = ParcheUTheme.colors.primary,
    otherEyeColor: Color = ParcheUPalette.Ink
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.Bottom
    ) {
        SimulationMascot(
            myColor,
            width,
            error = error,
            talking = speaker == TwinSpeaker.Me,
            animate = animate
        )
        SimulationMascot(
            otherColor,
            width,
            error = error,
            mirrored = true,
            talking = speaker == TwinSpeaker.Other,
            animate = animate,
            eyeColor = otherEyeColor
        )
    }
}

private const val TALK_PERIOD_MS = 260
private const val MOUTH_CLOSED_BELOW = 0.35f
private const val SWAY_DEGREES = 4f
private const val FOOT_X = 22f / 80f
private const val FOOT_Y = 86f / 90f

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
