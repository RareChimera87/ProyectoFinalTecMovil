package me.santiagoreina.parcheu.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Alpha mínimo de la escala de afinidad (afinidad ≤ 50). */
const val AFFINITY_ALPHA_MIN = 0.22f

/**
 * Escala de afinidad del inventario §3.3 (la fórmula de PU Grafo, que manda sobre la tabla de la hoja):
 * `clamp(.22 + .78 · (a - 50) / 45, .22, 1)`. 50 o menos da .22; 95 o más da 1.
 */
fun affinityAlpha(affinity: Float): Float =
    (AFFINITY_ALPHA_MIN + (1f - AFFINITY_ALPHA_MIN) * (affinity - 50f) / 45f).coerceIn(
        AFFINITY_ALPHA_MIN,
        1f,
    )

fun affinityAlpha(affinity: Int): Float = affinityAlpha(affinity.toFloat())

/**
 * En el tema claro, por encima de este alpha ni el texto oscuro ni el blanco llegan a 4,5:1 sobre el
 * violeta translúcido, así que el relleno se vuelve pleno (ADR-0030).
 */
const val LIGHT_AVATAR_DARK_TEXT_MAX_ALPHA = 0.80f

/**
 * Colores de un avatar de iniciales con relleno violeta. [fill] lleva el alpha efectivo y se pinta sobre
 * la superficie que haya debajo (bg, s1 o s2).
 */
@Immutable
data class AvatarColors(val fillAlpha: Float, val fill: Color, val content: Color)

/**
 * Regla de ADR-0030: en oscuro, texto blanco siempre; en claro, texto #14111F si `alpha ≤ 0,80` y, si
 * `alpha > 0,80`, el relleno se fija en alpha 1 con texto blanco (5,15:1).
 */
fun avatarColors(alpha: Float, darkTheme: Boolean): AvatarColors {
    val requested = alpha.coerceIn(0f, 1f)
    val fillAlpha =
        when {
            darkTheme -> requested
            requested > LIGHT_AVATAR_DARK_TEXT_MAX_ALPHA -> 1f
            else -> requested
        }
    return AvatarColors(
        fillAlpha = fillAlpha,
        fill = ParcheUPalette.Violet.copy(alpha = fillAlpha),
        content = onAvatarColor(requested, darkTheme),
    )
}

/** Color de las iniciales sobre el relleno de [avatarColors]. */
fun onAvatarColor(alpha: Float, darkTheme: Boolean): Color = when {
    darkTheme -> ParcheUPalette.White
    alpha > LIGHT_AVATAR_DARK_TEXT_MAX_ALPHA -> ParcheUPalette.White
    else -> ParcheUPalette.Ink
}
