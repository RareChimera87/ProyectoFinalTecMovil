package me.santiagoreina.parcheu.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalParcheUColors = staticCompositionLocalOf { DarkParcheUColors }
val LocalParcheUSpacing = staticCompositionLocalOf { ParcheUSpacing() }
val LocalParcheUShapes = staticCompositionLocalOf { ParcheUShapeTokens() }
val LocalParcheUTextStyles = staticCompositionLocalOf { ParcheUExtraTextStyles() }

/**
 * Tema de ParcheU: Material 3 con los tokens del inventario y las extensiones propias. El oscuro es el
 * principal; el claro es el de la Sección I. Sin color dinámico.
 */
@Composable
fun ParcheUTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkParcheUColors else LightParcheUColors
    CompositionLocalProvider(
        LocalParcheUColors provides colors,
        LocalParcheUSpacing provides ParcheUSpacing(),
        LocalParcheUShapes provides ParcheUShapeTokens(),
        LocalParcheUTextStyles provides ParcheUExtraTextStyles()
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = ParcheUTypography,
            shapes = ParcheUMaterialShapes,
            content = content
        )
    }
}

/** Acceso a los tokens propios dentro de [ParcheUTheme], al estilo de `MaterialTheme`. */
object ParcheUTheme {
    val colors: ParcheUColors
        @Composable @ReadOnlyComposable
        get() = LocalParcheUColors.current

    val spacing: ParcheUSpacing
        @Composable @ReadOnlyComposable
        get() = LocalParcheUSpacing.current

    val shapes: ParcheUShapeTokens
        @Composable @ReadOnlyComposable
        get() = LocalParcheUShapes.current

    val textStyles: ParcheUExtraTextStyles
        @Composable @ReadOnlyComposable
        get() = LocalParcheUTextStyles.current

    val isDark: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalParcheUColors.current.isDark
}
