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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.theme.ParcheUPalette
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Proporción alto / ancho de la mascota (docs/mockups-inventario.md, "Dimensión de mascota"). */
const val MASCOT_ASPECT_RATIO = 1.125f

/**
 * Ancho máximo al que se dibuja la variante simplificada de "La P que mira": sin boca ni brillo del
 * tallo y con el ojo más grande, para que a 24-30 dp se siga leyendo (docs/ui/parche-vivo.md).
 */
val MASCOT_SIMPLIFIED_MAX_WIDTH: Dp = 30.dp

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

/** Descripción sugerida para TalkBack cuando la mascota no es decorativa. */
const val TWIN_MASCOT_DESCRIPTION = "Tu gemelo, la P de ParcheU"

/**
 * Gemelo digital "La P que mira" (dirección A "Parche Vivo"): el monograma P de ParcheU cuya
 * contraforma es un ojo grande con brillo y, debajo, una boca de media luna. Se tiñe con el color del
 * rol ([tone]); el ojo y la boca van en tinta #14111F.
 *
 * - A [TwinMascotSize.S] (30 dp, ≤ [MASCOT_SIMPLIFIED_MAX_WIDTH]) se dibuja la variante simplificada.
 * - Con [talking] la boca se abre y cierra, el brillo del ojo mira de lado a lado y la P se balancea
 *   unos grados; si el sistema tiene la escala de animaciones en 0 (o [animationsEnabled] es falso)
 *   queda quieta en la pose de "conversando" (boca abierta).
 * - En [error] se dibuja en escala de grises con opacidad .5.
 * - Sin [contentDescription] es decorativa (TalkBack la salta); para anunciarla usa, por ejemplo,
 *   [TWIN_MASCOT_DESCRIPTION].
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

    val phase =
        if (talking && animationsEnabled) {
            rememberInfiniteTransition(label = "mascotTalking")
                .animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec =
                    infiniteRepeatable(
                        tween(TALK_PERIOD_MS, easing = LinearEasing),
                        RepeatMode.Reverse
                    ),
                    label = "mascotPhase"
                )
                .value
        } else {
            0f
        }

    val semanticsModifier =
        if (contentDescription != null) {
            Modifier.semantics {
                this.contentDescription = contentDescription
                mascotPhase = phase
            }
        } else {
            Modifier.clearAndSetSemantics { mascotPhase = phase }
        }

    val simplified = size.width <= MASCOT_SIMPLIFIED_MAX_WIDTH
    Canvas(
        modifier
            .size(size.width, size.height)
            .then(if (error) Modifier.alpha(ERROR_ALPHA) else Modifier)
            .then(semanticsModifier)
    ) {
        drawPMascot(
            body = body,
            eye = colors.onMode,
            simplified = simplified,
            mouth = if (talking) MascotMouth.Talking else MascotMouth.Quiet,
            phase = phase
        )
    }
}

/** Boca de la mascota: media luna quieta u óvalo de "conversando". */
enum class MascotMouth { Quiet, Talking }

/**
 * La misma P sin animación ni semántica, a cualquier ancho y con colores libres: para el cupo semanal
 * (`QuotaMascots`), el logo (`ParcheULogo`), el ícono de la app y las ilustraciones locales. Sigue la
 * regla de la mascota pequeña: con [width] ≤ [MASCOT_SIMPLIFIED_MAX_WIDTH] se dibuja simplificada.
 *
 * [eyeColor] es el color de la contraforma: tinta por defecto; sobre un bloque de color (p. ej. una P
 * blanca sobre el héroe violeta) pasa el color de ese fondo para que el ojo se lea como un hueco.
 * Es decorativa: el llamador pone la descripción accesible en el contenedor.
 *
 * [lookingLeft] (experimental, sin uso en pantallas) deja la P sin espejar pero corre el brillo al lado
 * izquierdo del ojo, para que "mire" hacia una P que está a su izquierda: alternativa a [mirrored], que
 * hace que la P del otro se lea como una "q". Por defecto es `false` y la mascota no cambia.
 */
@Composable
fun TwinMascotGlyph(
    width: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    eyeColor: Color = ParcheUPalette.Ink,
    mouth: MascotMouth = MascotMouth.Quiet,
    simplified: Boolean = width <= MASCOT_SIMPLIFIED_MAX_WIDTH,
    mirrored: Boolean = false,
    lookingLeft: Boolean = false
) {
    val lookLeft = if (simplified) LOOK_LEFT_SIMPLIFIED else LOOK_LEFT_UNITS
    val glance = if (lookingLeft) -lookLeft else 0f
    Canvas(modifier.size(width, width * MASCOT_ASPECT_RATIO).clearAndSetSemantics { }) {
        if (mirrored) {
            scale(-1f, 1f) { drawPMascot(color, eyeColor, simplified, mouth, 0f, glance) }
        } else {
            drawPMascot(color, eyeColor, simplified, mouth, 0f, glance)
        }
    }
}

/** Fase de la animación (0..1), expuesta solo para verificar en tests que la mascota se mueva o no. */
internal val MascotPhaseKey = SemanticsPropertyKey<Float>("MascotPhase")
private var SemanticsPropertyReceiver.mascotPhase by MascotPhaseKey

/** Lee `Settings.Global.ANIMATOR_DURATION_SCALE`: con 0 el usuario pidió no ver animaciones. */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) !=
                0f
        }
            .getOrDefault(true)
    }
}

/** Gris como CSS grayscale(1): luma Rec. 709 sobre componentes sRGB sin linealizar. */
internal fun Color.toGray(): Color {
    val l = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    return Color(l, l, l, alpha)
}

/*
 * Geometría de "La P que mira" en una caja de 80 × 90 unidades (la de los SVG de la propuesta,
 * gen_mascotas2.py, tarjeta 02). Todo se escala con k = ancho / 80.
 *
 *   P:      M10 20 Q10 8 22 8 H44 A28 28 0 0 1 44 64 H34 V76 Q34 86 22 86 Q10 86 10 76 Z
 *   Tallo:  M10 20 Q10 8 22 8 H34 V76 Q34 86 22 86 Q10 86 10 76 Z  (blanco al 24 %, solo completa)
 *   Ojo:    elipse (47, 32) 10 × 12,5 y brillo blanco (51, 26,5) r 3,4
 *   Boca:   media elipse (47, 51) 6,5 × 5,5 (quieta) u óvalo (47, 54) 5 × 4,2 (conversando)
 *   Simplificada (≤ 30 dp): sin tallo claro ni boca; ojo (47, 35) 13 × 16 y brillo (52, 28) r 4,5.
 */
internal fun DrawScope.drawPMascot(
    body: Color,
    eye: Color,
    simplified: Boolean,
    mouth: MascotMouth,
    phase: Float,
    glanceUnits: Float = 0f
) {
    val k = size.width / VIEW_W
    val animating = phase != 0f
    // Balanceo leve con pivote en el pie del tallo: -1 a 1 según la fase.
    val swing = if (animating) (phase - 0.5f) * 2f else 0f
    rotate(swing * SWAY_DEGREES, pivot = Offset(22f * k, 86f * k)) {
        drawPath(pPath(k), body)
        if (!simplified) drawPath(stemPath(k), Color.White.copy(alpha = STEM_HIGHLIGHT_ALPHA))

        val eyeCx = 47f * k
        val eyeCy = (if (simplified) 35f else 32f) * k
        val eyeRx = (if (simplified) 13f else 10f) * k
        val eyeRy = (if (simplified) 16f else 12.5f) * k
        drawOval(
            eye,
            topLeft = Offset(eyeCx - eyeRx, eyeCy - eyeRy),
            size = Size(eyeRx * 2, eyeRy * 2)
        )
        // Brillo: al conversar "mira" de lado a lado dentro del ojo.
        val glance = (swing * GLANCE_UNITS + glanceUnits) * k
        val glintX = (if (simplified) 52f else 51f) * k + glance
        val glintY = (if (simplified) 28f else 26.5f) * k
        val glintR = (if (simplified) 4.5f else 3.4f) * k
        drawCircle(Color.White, radius = glintR, center = Offset(glintX, glintY))

        if (!simplified) {
            when (mouth) {
                MascotMouth.Quiet -> drawPath(smilePath(k), eye)
                MascotMouth.Talking -> {
                    val open = if (animating) 0.35f + 0.65f * phase else 1f
                    val rx = 5f * k
                    val ry = 4.2f * k * open
                    drawOval(
                        eye,
                        topLeft = Offset(47f * k - rx, 54f * k - ry),
                        size = Size(rx * 2, ry * 2)
                    )
                }
            }
        }
    }
}

private fun pPath(k: Float): Path = Path().apply {
    moveTo(10f * k, 20f * k)
    quadraticTo(10f * k, 8f * k, 22f * k, 8f * k)
    lineTo(44f * k, 8f * k)
    // Arco de 28 alrededor de (44, 36): de arriba (44, 8) a abajo (44, 64) por la derecha.
    arcTo(Rect(16f * k, 8f * k, 72f * k, 64f * k), -90f, 180f, false)
    lineTo(34f * k, 64f * k)
    lineTo(34f * k, 76f * k)
    quadraticTo(34f * k, 86f * k, 22f * k, 86f * k)
    quadraticTo(10f * k, 86f * k, 10f * k, 76f * k)
    close()
}

private fun stemPath(k: Float): Path = Path().apply {
    moveTo(10f * k, 20f * k)
    quadraticTo(10f * k, 8f * k, 22f * k, 8f * k)
    lineTo(34f * k, 8f * k)
    lineTo(34f * k, 76f * k)
    quadraticTo(34f * k, 86f * k, 22f * k, 86f * k)
    quadraticTo(10f * k, 86f * k, 10f * k, 76f * k)
    close()
}

private fun smilePath(k: Float): Path = Path().apply {
    val rx = 6.5f * k
    val ry = 5.5f * k
    val cx = 47f * k
    val cy = 51f * k
    moveTo(cx + rx, cy)
    arcTo(Rect(cx - rx, cy - ry, cx + rx, cy + ry), 0f, 180f, false)
    close()
}

private const val VIEW_W = 80f
private const val ERROR_ALPHA = 0.5f
private const val STEM_HIGHLIGHT_ALPHA = 0.24f
private const val SWAY_DEGREES = 4f
private const val GLANCE_UNITS = 2.5f

/** Corrimiento del brillo con `lookingLeft`: al lado izquierdo del ojo (51 → 43; simplificada 52 → 42). */
private const val LOOK_LEFT_UNITS = 8f
private const val LOOK_LEFT_SIMPLIFIED = 10f
private const val TALK_PERIOD_MS = 420
