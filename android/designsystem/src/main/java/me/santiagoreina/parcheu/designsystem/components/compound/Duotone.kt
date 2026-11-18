package me.santiagoreina.parcheu.designsystem.components.compound

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import me.santiagoreina.parcheu.designsystem.theme.ModeColors
import me.santiagoreina.parcheu.designsystem.theme.ParcheUPalette
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/**
 * Matriz de duotono: pasa cada píxel a su luminancia (Rec. 709) y la reparte entre [shadow] (negro) y
 * [highlight] (blanco). Conserva el alfa. Es la base de [duotoneColorFilter] y [Modifier.duotone].
 */
fun duotoneColorMatrix(shadow: Color, highlight: Color): ColorMatrix {
    fun row(s: Float, h: Float): FloatArray {
        val d = h - s
        return floatArrayOf(d * LUMA_R, d * LUMA_G, d * LUMA_B, 0f, s * 255f)
    }
    val r = row(shadow.red, highlight.red)
    val g = row(shadow.green, highlight.green)
    val b = row(shadow.blue, highlight.blue)
    return ColorMatrix(r + g + b + floatArrayOf(0f, 0f, 0f, 1f, 0f))
}

/** Filtro de duotono para `Image(colorFilter = …)`: sombras en [shadow] y luces en [highlight]. */
fun duotoneColorFilter(shadow: Color, highlight: Color): ColorFilter =
    ColorFilter.colorMatrix(duotoneColorMatrix(shadow, highlight))

/**
 * Duotono de marca (dirección A): sombras en tinta #14111F y luces en el color pleno del [mode]. Así las
 * fotos de stock (los WebP de drawable-nodpi de B1) se ven de la misma familia. Se aplica a todo lo que
 * dibuja el nodo (una `Image`, un `AsyncImage`, un `Box` con fondo), por eso va DESPUÉS del recorte.
 *
 * ```
 * Image(painterResource(R.drawable.twin_q01_a), null, Modifier.clip(ParcheUTheme.shapes.photo).duotone(colors.study))
 * ```
 */
fun Modifier.duotone(mode: ModeColors, shadow: Color = ParcheUPalette.Ink): Modifier =
    duotone(shadow = shadow, highlight = mode.solid)

/** Duotono con colores libres; ver [Modifier.duotone] por modo. */
fun Modifier.duotone(shadow: Color, highlight: Color): Modifier {
    val paint = Paint().apply { colorFilter = duotoneColorFilter(shadow, highlight) }
    return drawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
            drawContent()
            canvas.restore()
        }
    }
}

/**
 * Foto con duotono del [mode] y esquinas de 20 (`ParcheUTheme.shapes.photo`). Atajo para las tarjetas de
 * opción con imagen de B1; por defecto llena el ancho y recorta al alto que le dé [modifier].
 */
@Composable
fun DuotonePhoto(
    painter: Painter,
    mode: ModeColors,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = contentScale,
        colorFilter = duotoneColorFilter(ParcheUPalette.Ink, mode.solid),
        modifier = modifier.fillMaxWidth().clip(ParcheUTheme.shapes.photo)
    )
}

private const val LUMA_R = 0.2126f
private const val LUMA_G = 0.7152f
private const val LUMA_B = 0.0722f
