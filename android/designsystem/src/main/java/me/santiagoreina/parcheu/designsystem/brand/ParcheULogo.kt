package me.santiagoreina.parcheu.designsystem.brand

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import kotlin.math.roundToInt
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotGlyph
import me.santiagoreina.parcheu.designsystem.theme.ParcheUPalette
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.designsystem.theme.SoraFontFamily

/**
 * Lockup horizontal de la dirección A: "La P que mira" hace de P y sigue "arche" en Sora 700 con la "U"
 * naranja. Para TalkBack es un solo nodo "ParcheU".
 *
 * La P mide 0,956 × el tamaño de la letra (39 px de mascota para 40,8 px de texto en la propuesta) y su
 * pie se apoya en la línea base. "arche" toma [ParcheUTheme.colors]`.wordmark` (blanco en oscuro y
 * violeta en claro); la P es violeta en ambos temas con el ojo en tinta.
 */
@Composable
fun ParcheULogo(
    modifier: Modifier = Modifier,
    size: WordmarkSize = WordmarkSize.Medium,
    textColor: Color = ParcheUTheme.colors.wordmark,
    mascotColor: Color = ParcheUPalette.Violet
) {
    val mascotWidth: Dp = with(LocalDensity.current) { size.fontSize.toDp() } * LOGO_MASCOT_RATIO
    Row(
        modifier.clearAndSetSemantics { contentDescription = "ParcheU" },
        verticalAlignment = Alignment.Bottom
    ) {
        TwinMascotGlyph(
            width = mascotWidth,
            color = mascotColor,
            // El lockup siempre lleva la P completa, también a 20 sp.
            simplified = false,
            modifier =
            Modifier
                .alignBy { (it.measuredHeight * PIE_FRACTION).roundToInt() }
                // La caja de 80 deja 10 unidades libres a la izquierda y 8 a la derecha de la P: se
                // recortan para que la P quede pegada a "arche" como una letra más.
                .layout { measurable, constraints ->
                    val p = measurable.measure(constraints)
                    val left = (p.width * SIDE_LEFT).roundToInt()
                    val right = (p.width * SIDE_RIGHT).roundToInt()
                    layout(p.width - left - right, p.height) { p.place(-left, 0) }
                }
        )
        Spacer(Modifier.width(mascotWidth * GAP_RATIO))
        Text(
            text =
            buildAnnotatedString {
                withStyle(SpanStyle(color = textColor)) { append("arche") }
                withStyle(SpanStyle(color = WordmarkColors.Orange)) { append("U") }
            },
            style =
            TextStyle(
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = size.fontSize,
                letterSpacing = (-0.02).em
            ),
            modifier = Modifier.alignByBaseline()
        )
    }
}

/**
 * Ícono de la app como composable (splash en Compose, onboarding, ParcheU+): cuadrado violeta con radio
 * 27 % y la P blanca con el ojo del color del fondo (contraforma). Es la misma geometría del ícono
 * adaptativo `ic_launcher_foreground`. Decorativo.
 */
@Composable
fun ParcheUAppIcon(
    size: Dp,
    modifier: Modifier = Modifier,
    background: Color = ParcheUPalette.Violet,
    foreground: Color = ParcheUPalette.White
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 27))
            .background(background)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        TwinMascotGlyph(
            width = size * ICON_MASCOT_RATIO,
            color = foreground,
            eyeColor = background
        )
    }
}

private const val LOGO_MASCOT_RATIO = 0.956f
private const val GAP_RATIO = 0.06f
private const val SIDE_LEFT = 10f / 80f
private const val SIDE_RIGHT = 8f / 80f

/** Fracción del alto de la caja de la mascota en la que está el pie de la P (86 / 90). */
private const val PIE_FRACTION = 86f / 90f

/** Ancho de la caja de la mascota dentro del ícono (44 de 72 en la propuesta). */
private const val ICON_MASCOT_RATIO = 44f / 72f
