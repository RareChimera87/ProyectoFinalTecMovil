package me.santiagoreina.parcheu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors =
    darkColorScheme(
        primary = Violet,
        onPrimary = Color.White,
        primaryContainer = PrimaryContainer,
        onPrimaryContainer = OnPrimaryContainer,
        background = Ink,
        onBackground = TextPrimary,
        surface = Ink,
        onSurface = TextPrimary,
        surfaceVariant = Surface2,
        onSurfaceVariant = TextMuted,
        surfaceContainer = Surface1,
        outline = Outline,
        // prt: texto y enlaces sobre fondo oscuro.
        tertiary = PrimaryTint
    )

private val LightColors =
    lightColorScheme(
        primary = Violet,
        onPrimary = Color.White,
        background = Bone,
        onBackground = LightText,
        surface = Bone,
        onSurface = LightText
    )

/**
 * Tema provisional del esqueleto. El oscuro es el principal (inventario §3.1). Sin color dinámico:
 * la marca no cambia con el fondo de pantalla del usuario (`dynamicColor = false`).
 */
@Composable
fun ParcheUTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
