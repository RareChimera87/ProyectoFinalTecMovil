package me.santiagoreina.parcheu.feature.twinquestions

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import kotlin.math.max
import me.santiagoreina.parcheu.R
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/*
 * Duotono de las fotos de B1 (dirección A "Parche Vivo"). El duotono compartido (`Modifier.duotone`) reparte la
 * luminancia tal cual entre tinta y el color del modo, así que una foto nocturna (la fogata de q01, la lámpara
 * de q02, el celular en lo oscuro de q09) queda casi toda en tinta y no se lee. Aquí, antes del duotono, se
 * sube la exposición de esas fotos con una ganancia por foto y se levanta el negro un poco para todas: la
 * foto sigue en la familia de color pero se distingue de la tarjeta, en claro y en oscuro.
 */

/** Levante del negro (fracción de la luminancia) para todas las fotos de B1: el negro puro se pierde en s1. */
internal const val B1_PHOTO_LIFT = 0.05f

/**
 * Ganancia de exposición de las fotos oscuras: ≈ 0,36 / luminancia media de la foto, con tope 3. Las medias se
 * midieron sobre los WebP de drawable-nodpi (`magick … -colorspace Gray -format %[fx:mean]`); las fotos con
 * media ≥ 0,30 no la necesitan.
 */
private val photoGain: Map<Int, Float> =
    mapOf(
        R.drawable.twin_q01_a to 2.6f, // fogata de noche (media 0,14)
        R.drawable.twin_q02_b to 3.0f, // lámpara y libros en penumbra (0,05)
        R.drawable.twin_q09_b to 3.0f, // manos con el celular en lo oscuro (0,10)
        R.drawable.twin_q10_b to 1.6f, // siluetas al atardecer (0,23)
        R.drawable.twin_q11_a to 1.6f, // luces de bengala (0,23)
        R.drawable.twin_q08_b to 1.4f, // silueta en el horizonte (0,26)
        R.drawable.twin_q04_b to 1.2f, // (0,29)
        R.drawable.twin_q01_b to 1.15f // (0,31)
    )

/** Ganancia de exposición de la foto [image] (1 = sin cambio). */
internal fun photoGainFor(@DrawableRes image: Int): Float = photoGain[image] ?: 1f

/**
 * Pantalla del celular de `twin_q09_a` en coordenadas de la foto (fracción de 720 × 450): ahí se ve la marca de
 * un festival ("OFF FESTIVAL"), que con el duotono y al tamaño real de la tarjeta se seguía leyendo. La tarjeta
 * casi no recorta la foto (su proporción es parecida), así que cambiar el encuadre no la saca; en su lugar
 * [Modifier.photoVeil] pinta encima un velo liso del color que tiene la pantalla en duotono, como si estuviera
 * en blanco. El archivo no se toca.
 */
private val q09ScreenQuad: List<Offset> =
    listOf(
        Offset(346f / 720f, 121f / 450f),
        Offset(454f / 720f, 77f / 450f),
        Offset(481f / 720f, 135f / 450f),
        Offset(394f / 720f, 172f / 450f)
    )

/** Zona a velar de la foto [image] (en fracciones de la foto), o null si no hace falta. */
private fun veilQuadFor(@DrawableRes image: Int): List<Offset>? = when (image) {
    R.drawable.twin_q09_a -> q09ScreenQuad
    else -> null
}

/**
 * Velo de la zona de [image] que no debe leerse (ver [q09ScreenQuad]). Supone la foto dibujada con
 * `ContentScale.Crop` y centrada, de tamaño intrínseco [intrinsic]; [color] es el de la zona ya en duotono.
 */
internal fun Modifier.photoVeil(@DrawableRes image: Int, intrinsic: Size, color: Color): Modifier {
    val quad = veilQuadFor(image) ?: return this
    if (intrinsic.isUnspecified || intrinsic.width <= 0f || intrinsic.height <= 0f) return this
    return drawWithContent {
        drawContent()
        val scale = max(size.width / intrinsic.width, size.height / intrinsic.height)
        val dx = (size.width - intrinsic.width * scale) / 2f
        val dy = (size.height - intrinsic.height * scale) / 2f
        val path =
            Path().apply {
                quad.forEachIndexed { i, p ->
                    val x = dx + p.x * intrinsic.width * scale
                    val y = dy + p.y * intrinsic.height * scale
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        drawPath(path, color)
    }
}

/** Color que toma en el duotono de B1 un píxel de luminancia [luma] (lo que pinta [b1DuotoneFilter]). */
internal fun b1DuotoneColor(shadow: Color, highlight: Color, gain: Float, luma: Float): Color =
    lerp(shadow, highlight, (luma * gain + B1_PHOTO_LIFT).coerceIn(0f, 1f))

/**
 * Filtro de B1: luminancia (Rec. 709) × [gain] + [lift], repartida entre [shadow] (la tinta) y [highlight] (el
 * color pleno del modo). Es una sola `ColorMatrix`: lo que pasa de 1 se satura en el color del modo, que es lo que se
 * busca en el fuego o en una lámpara.
 */
internal fun b1DuotoneFilter(
    shadow: Color,
    highlight: Color,
    gain: Float,
    lift: Float = B1_PHOTO_LIFT
): ColorFilter {
    fun row(s: Float, h: Float): FloatArray {
        val d = h - s
        return floatArrayOf(
            d * gain * LUMA_R,
            d * gain * LUMA_G,
            d * gain * LUMA_B,
            0f,
            (s + d * lift) * 255f
        )
    }
    val matrix =
        row(shadow.red, highlight.red) +
            row(shadow.green, highlight.green) +
            row(shadow.blue, highlight.blue) +
            floatArrayOf(0f, 0f, 0f, 1f, 0f)
    return ColorFilter.colorMatrix(ColorMatrix(matrix))
}

/**
 * Color de las luces de las dos fotos de cada pregunta: la opción A va en el pleno del modo con el que más
 * tiene que ver la pregunta (estudio para el parcial, el semillero, la nota y el grupo de trabajo; pareja para
 * el desacuerdo con alguien cercano; amistad para el resto) y la B siempre en violeta de marca, como en el
 * mockup A (el violeta claro `heroQuiet`, para que las luces no queden tan oscuras como el violeta pleno). Es
 * decorativo: B1 no pertenece a ningún modo y no se dice en texto.
 */
@Composable
internal fun questionPhotoHighlights(questionId: Int): Pair<Color, Color> {
    val colors = ParcheUTheme.colors
    val a =
        when (questionId) {
            2, 3, 5, 10 -> colors.study.solid
            8 -> colors.couple.solid
            else -> colors.friendship.solid
        }
    return a to colors.heroQuiet
}

private const val LUMA_R = 0.2126f
private const val LUMA_G = 0.7152f
private const val LUMA_B = 0.0722f
