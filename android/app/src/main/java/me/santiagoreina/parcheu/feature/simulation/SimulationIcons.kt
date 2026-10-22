package me.santiagoreina.parcheu.feature.simulation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

/*
 * Iconos Material Symbols (24dp) que usan F4-F6 mientras el set de marca de E1.2 no está en el
 * módulo designsystem. Son contornos vectoriales propios de la pantalla; al llegar E1.2 se cambian por
 * los del sistema de diseño sin tocar el resto.
 */

private fun icon(name: String, path: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
    .build()

internal object SimulationIcons {
    val Close =
        icon(
            "close",
            "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z",
        )
    val ArrowBack =
        icon("arrow_back", "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z")
    val Refresh =
        icon(
            "refresh",
            "M17.65 6.35A7.958 7.958 0 0 0 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0 1 12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z",
        )
    val CheckCircle =
        icon(
            "check_circle",
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z",
        )
    val RadioUnchecked =
        icon(
            "radio_button_unchecked",
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8z",
        )
    val Notifications =
        icon(
            "notifications",
            "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z",
        )
    val Hub =
        icon(
            "hub",
            "M8.4 18.2c.4.6.6 1.3.6 2 0 1.7-1.3 3-3 3s-3-1.3-3-3 1.3-3 3-3c.7 0 1.4.2 1.9.6l1.3-1.5C8.3 16.1 8 15.1 8 14h2c0 .8.2 1.5.7 2.1l-.5.6.2.5zM12 2C9.8 2 8 3.8 8 6s1.8 4 4 4 4-1.8 4-4-1.8-4-4-4zm6 15c-.7 0-1.4.2-1.9.6l-1.3-1.5c.5-.6.8-1.4.8-2.2h-2c0 .8-.2 1.5-.7 2.1l1.2 1.3c-.4.6-.6 1.3-.6 2 0 1.7 1.3 3 3 3s3-1.3 3-3-1.3-3-3-3z",
        )
}

@Composable
internal fun rememberIcon(vector: ImageVector): Painter = rememberVectorPainter(vector)
