package me.santiagoreina.parcheu.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import me.santiagoreina.parcheu.designsystem.R

/**
 * Sora (400/600/700): títulos, números, iniciales y precios. Empaquetada en res/font con licencia OFL 1.1
 * (android/designsystem/licenses/OFL-Sora.txt).
 */
val SoraFontFamily =
    FontFamily(
        Font(R.font.sora_regular, FontWeight.Normal),
        Font(R.font.sora_semibold, FontWeight.SemiBold),
        Font(R.font.sora_bold, FontWeight.Bold)
    )

/** Inter (400/500/600/700): interfaz y cuerpo. OFL 1.1 (android/designsystem/licenses/OFL-Inter.txt). */
val InterFontFamily =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold)
    )

private fun sora(size: Int, weight: FontWeight, lineHeight: Int, letterSpacing: Float = 0f) =
    TextStyle(
        fontFamily = SoraFontFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = letterSpacing.sp
    )

private fun inter(size: Int, weight: FontWeight, lineHeight: Int, letterSpacing: Float = 0f) =
    TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = letterSpacing.sp
    )

/**
 * Escala del inventario §3.4 sobre los roles de Material 3:
 * - display: 44 del splash, 34 de la hoja y 31 de la afinidad (Sora 700).
 * - headline: 28, 26 y 22 (títulos de hero y de pantalla).
 * - title: 19/600 app bar, 17/600 nombres, 14/600 nombres pequeños.
 * - body: Inter 16, 15 y 13.
 * - label: Inter 15/600 botones, 13/500 chips, 11/500 secundarios.
 */
val ParcheUTypography =
    Typography(
        displayLarge = sora(44, FontWeight.Bold, 52),
        displayMedium = sora(34, FontWeight.Bold, 40),
        displaySmall = sora(31, FontWeight.Bold, 38),
        headlineLarge = sora(28, FontWeight.Bold, 34),
        headlineMedium = sora(26, FontWeight.Bold, 32),
        headlineSmall = sora(22, FontWeight.SemiBold, 28),
        titleLarge = sora(19, FontWeight.SemiBold, 24),
        titleMedium = sora(17, FontWeight.SemiBold, 22),
        titleSmall = sora(14, FontWeight.SemiBold, 20),
        bodyLarge = inter(16, FontWeight.Normal, 24),
        bodyMedium = inter(15, FontWeight.Normal, 22),
        bodySmall = inter(13, FontWeight.Normal, 18),
        labelLarge = inter(15, FontWeight.SemiBold, 20),
        labelMedium = inter(13, FontWeight.Medium, 16),
        labelSmall = inter(11, FontWeight.Medium, 14)
    )

/** Estilos del inventario que no tienen rol en Material 3. */
@Immutable
data class ParcheUExtraTextStyles(
    /** Número gigante de la hoja de sistema (S0). */
    val hero: TextStyle = sora(88, FontWeight.Bold, 96),
    /** Overline 12/600 con espaciado .6; el texto se pasa en mayúsculas desde la pantalla. */
    val overline: TextStyle = inter(12, FontWeight.SemiBold, 16, letterSpacing = 0.6f),
    /** Iniciales de avatares y nodos del grafo. */
    val initials: TextStyle = sora(14, FontWeight.SemiBold, 18),
    /** Precios del paywall y de ParcheU+. */
    val price: TextStyle = sora(24, FontWeight.Bold, 30)
)
