package me.santiagoreina.parcheu.feature.modes

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.designsystem.theme.ModeColors
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/*
 * Textos, colores e íconos de los modos y del flujo B2-B5 (E2.5). Los íconos son trazos de Material
 * Symbols dibujados como ImageVector mientras no se mezcla el set de íconos de E1.2 (mismo enfoque que
 * B1 en E2.4); al llegar E1.2 se reemplazan por sus recursos.
 */

/** Nombre visible del modo. */
val Mode.label: String
    get() = when (this) {
        Mode.AMISTAD -> "Amistad"
        Mode.PAREJA -> "Pareja"
        Mode.ESTUDIO -> "Estudio"
    }

/** Descripción corta de B2. */
val Mode.description: String
    get() = when (this) {
        Mode.AMISTAD -> "Encuentra parche y amigos nuevos."
        Mode.PAREJA -> "Conoce a alguien. Solo para mayores de 18."
        Mode.ESTUDIO -> "Compañeros para trabajos y proyectos."
    }

/** Colores del modo (Amistad menta, Pareja coral, Estudio azul). */
@Composable
fun Mode.colors(): ModeColors = when (this) {
    Mode.AMISTAD -> ParcheUTheme.colors.friendship
    Mode.PAREJA -> ParcheUTheme.colors.couple
    Mode.ESTUDIO -> ParcheUTheme.colors.study
}

/** Ícono del modo: diversity_3 (grupo), favorite y school. */
@Composable
fun Mode.icon(): Painter = rememberVectorPainter(
    when (this) {
        Mode.AMISTAD -> FlowIcons.Group
        Mode.PAREJA -> FlowIcons.Favorite
        Mode.ESTUDIO -> FlowIcons.School
    },
)

/** Íconos de 24dp del flujo B2-B5. */
object FlowIcons {
    val Back: ImageVector = icon("arrow_back", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    val Favorite: ImageVector = icon(
        "favorite",
        "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09" +
            "C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z",
    )
    val School: ImageVector = icon(
        "school",
        "M5,13.18v4L12,21l7,-3.82v-4L12,17l-7,-3.82zM12,3L1,9l11,6 9,-4.91V17h2V9L12,3z",
    )
    val Group: ImageVector = icon(
        "group",
        "M16,11c1.66,0 2.99,-1.34 2.99,-3S17.66,5 16,5c-1.66,0 -3,1.34 -3,3s1.34,3 3,3z" +
            "M8,11c1.66,0 2.99,-1.34 2.99,-3S9.66,5 8,5C6.34,5 5,6.34 5,8s1.34,3 3,3z" +
            "M8,13c-2.33,0 -7,1.17 -7,3.5V19h14v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z" +
            "M16,13c-0.29,0 -0.62,0.02 -0.97,0.05 1.16,0.84 1.97,1.97 1.97,3.45V19h6v-2.5" +
            "c0,-2.33 -4.67,-3.5 -7,-3.5z",
    )
    val Info: ImageVector = icon(
        "info",
        "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2V7h2v2z",
    )
    val Edit: ImageVector = icon(
        "edit_note",
        "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41" +
            "l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z",
    )
    val Star: ImageVector = icon(
        "interests",
        "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z",
    )
    val Handshake: ImageVector = icon(
        "handshake",
        "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z" +
            "M10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-8,9z",
    )
    val Block: ImageVector = icon(
        "block",
        "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z" +
            "M4,12c0,-4.42 3.58,-8 8,-8 1.85,0 3.55,0.63 4.9,1.69L5.69,16.9C4.63,15.55 4,13.85 4,12z" +
            "M12,20c-1.85,0 -3.55,-0.63 -4.9,-1.69L18.31,7.1C19.37,8.45 20,10.15 20,12c0,4.42 -3.58,8 -8,8z",
    )

    private fun icon(name: String, pathData: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
        .build()
}
