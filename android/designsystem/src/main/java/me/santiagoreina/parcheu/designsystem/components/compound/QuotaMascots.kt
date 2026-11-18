package me.santiagoreina.parcheu.designsystem.components.compound

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotGlyph
import me.santiagoreina.parcheu.designsystem.theme.ParcheUSizes
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.designsystem.theme.SoraFontFamily

/**
 * Colores de [QuotaMascots]: mascota encendida ([lit]), apagada ([off]) y el ojo ([eye], que debe ser el
 * color del fondo para leerse como contraforma).
 */
@Immutable
data class QuotaMascotsColors(val lit: Color, val off: Color, val eye: Color)

object QuotaMascotsDefaults {
    /** Sobre el [HeroBlock] violeta: blancas encendidas, #8F75FF apagadas y ojo violeta. */
    @Composable
    fun onHero(): QuotaMascotsColors {
        val c = ParcheUTheme.colors
        return QuotaMascotsColors(lit = c.onHero, off = c.heroQuiet, eye = c.hero)
    }

    /** Sobre una superficie (bg, s1): violeta encendidas, ln apagadas y ojo en tinta. */
    @Composable
    fun onSurface(): QuotaMascotsColors {
        val c = ParcheUTheme.colors
        return QuotaMascotsColors(lit = c.primary, off = c.ln, eye = c.onMode)
    }

    /** Máximo de mascotas que se dibujan; con más cupo se muestra este número (el texto dice el real). */
    const val MAX_VISIBLE = 10
}

/** Texto accesible por defecto de [QuotaMascots]. */
fun quotaMascotsDescription(used: Int, limit: Int?): String = if (limit == null) {
    "Simulaciones sin límite con ParcheU+"
} else {
    val left = (limit - used).coerceIn(0, limit)
    "Te quedan $left de $limit simulaciones esta semana"
}

/**
 * Cupo semanal como fila de mascotas (dirección A "Parche Vivo"): la mascota es la unidad de medida.
 * Hay [limit] mascotas y brillan las que te quedan (`limit − used`); las gastadas quedan apagadas. Con
 * [limit] nulo (ParcheU+) se dibujan [unlimitedSlots] mascotas encendidas y el signo ∞.
 *
 * Es un solo nodo para TalkBack con [contentDescription] (por defecto [quotaMascotsDescription], p. ej.
 * "Te quedan 2 de 5 simulaciones esta semana"). Usa la variante simplificada de la mascota (24 dp).
 */
@Composable
fun QuotaMascots(
    used: Int,
    limit: Int?,
    modifier: Modifier = Modifier,
    colors: QuotaMascotsColors = QuotaMascotsDefaults.onHero(),
    mascotWidth: Dp = ParcheUSizes.QuotaMascot,
    unlimitedSlots: Int = 5,
    contentDescription: String = quotaMascotsDescription(used, limit)
) {
    val slots = (limit ?: unlimitedSlots).coerceIn(0, QuotaMascotsDefaults.MAX_VISIBLE)
    val left = if (limit == null) slots else (limit - used).coerceIn(0, slots)
    Row(
        modifier.clearAndSetSemantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(slots) { i ->
            TwinMascotGlyph(
                width = mascotWidth,
                color = if (i < left) colors.lit else colors.off,
                eyeColor = colors.eye
            )
        }
        if (limit == null) {
            Text(
                text = "∞",
                color = colors.lit,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp
            )
        }
    }
}
