package me.santiagoreina.parcheu.feature.invites

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/*
 * Iconos de C5 (E6.1) como vectores propios de 24dp con los trazados de Material Symbols, igual que
 * las
 * demás features mientras E1.2 no publique el set oficial, y el ícono de la app para el centro del
 * QR.
 */

private fun icon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
        .build()

internal object InvitesIcons {
    val Back: ImageVector by lazy {
        icon("arrow_back", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    }

    val Copy: ImageVector by lazy {
        icon(
            "content_copy",
            "M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1zM19,5H8c-1.1,0 -2,0.9" +
                " -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9," +
                "-2 -2,-2zM19,21H8V7h11v14z"
        )
    }

    val Share: ImageVector by lazy {
        icon(
            "share",
            "M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 " +
                "0.09,-0.46 0.09,-0.7s-0.04,-0.47 -0.09,-0.7l7.05,-4.11c0.54," +
                "0.5 1.25,0.81 2.04,0.81 1.66,0 3,-1.34 3,-3s-1.34,-3 -3,-3 -" +
                "3,1.34 -3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.7" +
                "9,9 6,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3c0.79,0 1.5,-0.31 2.0" +
                "4,-0.81l7.12,4.16c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.61 1." +
                "31,2.92 2.92,2.92s2.92,-1.31 2.92,-2.92 -1.31,-2.92 -2.92,-2" +
                ".92z"
        )
    }

    val Scanner: ImageVector by lazy {
        icon(
            "qr_code_scanner",
            "M9.5,6.5v3h-3v-3H9.5M11,5H5v6h6V5L11,5zM9.5,14.5v3h-3v-3H9.5" +
                "M11,13H5v6h6V13L11,13zM17.5,6.5v3h-3v-3H17.5M19,5h-6v6h6V5L1" +
                "9,5zM13,13h1.5v1.5H13V13zM14.5,14.5H16V16h-1.5V14.5zM16,13h1" +
                ".5v1.5H16V13zM13,16h1.5v1.5H13V16zM14.5,17.5H16V19h-1.5V17.5" +
                "zM16,16h1.5v1.5H16V16zM17.5,14.5H19V16h-1.5V14.5zM17.5,17.5H" +
                "19V19h-1.5V17.5zM22,7h-2V4h-3V2h5V7zM22,22v-5h-2v3h-3v2H22zM" +
                "2,22h5v-2H4v-3H2V22zM2,2v5h2V4h3V2H2z"
        )
    }

    val PersonAdd: ImageVector by lazy {
        icon(
            "person_add",
            "M15,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79," +
                "4 4,4zM6,10V7H4v3H1v2h3v3h2v-3h3v-2H6zM15,14c-2.67,0 -8,1.34" +
                " -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z"
        )
    }

    /** Ícono de la app (inventario: AppIcon 96x96, rx 26, #6D4AFF, U blanca y nodo #FF8A3D). */
    val AppIcon: ImageVector by lazy {
        ImageVector.Builder("parcheu", 96.dp, 96.dp, 96f, 96f)
            .addPath(
                addPathNodes(
                    "M26,0H70A26,26 0 0,1 96,26V70A26,26 0 0,1 70,96H26A26,26 0 0" +
                        ",1 0,70V26A26,26 0 0,1 26,0Z"
                ),
                fill = SolidColor(Color(0xFF6D4AFF))
            )
            .addPath(
                addPathNodes("M30,24L30,52L48,70L66,52L66,24"),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            .addPath(
                addPathNodes("M48,61A9,9 0 1,1 47.99,61Z"),
                fill = SolidColor(Color(0xFFFF8A3D))
            )
            .build()
    }
}
