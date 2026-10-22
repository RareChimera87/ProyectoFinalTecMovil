package me.santiagoreina.parcheu.designsystem.brand

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Colores de marca del wordmark (mockups-inventario.md, sección Wordmark ParcheU). */
object WordmarkColors {
    /** "U" en ambos temas. */
    val Orange = Color(0xFFFF8A3D)

    /** "Parche" en el tema oscuro. */
    val OnDark = Color(0xFFFFFFFF)

    /** "Parche" en el tema claro (violeta primario). */
    val OnLight = Color(0xFF6D4AFF)

    /** Color de "Parche" según el tema de la app (no el del sistema). */
    fun base(darkTheme: Boolean): Color = if (darkTheme) OnDark else OnLight
}

/** Tamaños del wordmark en sp: app bar (20, 22) y splash (44). */
enum class WordmarkSize(val fontSize: TextUnit) {
    Small(20.sp),
    Medium(22.sp),
    Large(44.sp)
}

/**
 * Wordmark "ParcheU": Sora 700 con la "U" en #FF8A3D. "Parche" es blanco en oscuro y #6D4AFF en claro.
 * La fuente Sora llega con E1.1 (tipografía); mientras tanto usa la fuente por defecto en negrita.
 *
 * [darkTheme] es obligatorio a propósito: el tema de la app tiene preferencia propia en DataStore y
 * puede diferir del sistema, así que el llamador pasa el valor del tema de la app (el de `ParcheUTheme`
 * de E1.1), nunca `isSystemInDarkTheme()` por defecto.
 */
@Composable
fun Wordmark(
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    size: WordmarkSize = WordmarkSize.Medium
) {
    val base = WordmarkColors.base(darkTheme)
    Text(
        text =
        buildAnnotatedString {
            withStyle(SpanStyle(color = base)) { append("Parche") }
            withStyle(SpanStyle(color = WordmarkColors.Orange)) { append("U") }
        },
        modifier = modifier,
        fontSize = size.fontSize,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    )
}
