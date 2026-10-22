package me.santiagoreina.parcheu.designsystem.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Espaciado del inventario §3.5. */
@Immutable
data class ParcheUSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    /** Gutter horizontal de pantalla. */
    val gutter: Dp = 16.dp,
    /** Gutter de onboarding y de las pantallas de celebración (B5, G3). */
    val gutterWide: Dp = 24.dp,
    /** Separación entre bloques de una pantalla. */
    val blockGap: Dp = 14.dp,
    val cardPadding: Dp = 16.dp,
    /** Footer de acciones: 12 arriba, 16 a los lados y 28 abajo. */
    val footer: PaddingValues =
        PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
    /** Footer de celebración (B5, G3): 12 / 24 / 32. */
    val footerCelebration: PaddingValues =
        PaddingValues(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 32.dp),
)

/** Tamaños fijos de componentes del inventario §3.5. La NavBar usa insets, no un relleno fijo. */
object ParcheUSizes {
    val AppBar: Dp = 56.dp
    val NavBar: Dp = 76.dp
    val Button: Dp = 52.dp
    val ButtonCompact: Dp = 40.dp
    val ButtonSmall: Dp = 36.dp
    val IconButton: Dp = 48.dp
    val FilterChip: Dp = 32.dp
    val ModeChipMedium: Dp = 28.dp
    val ModeChipSmall: Dp = 24.dp
    val SwitchWidth: Dp = 52.dp
    val SwitchHeight: Dp = 32.dp
    val Field: Dp = 56.dp
}
