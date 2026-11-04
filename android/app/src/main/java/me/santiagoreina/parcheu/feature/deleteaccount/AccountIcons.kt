package me.santiagoreina.parcheu.feature.deleteaccount

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/*
 * Iconos de borrar cuenta y descargar mis datos (E8.3) como vectores propios de 24dp con los trazados de
 * Material Symbols, igual que H1/H2 (E8.1): sin depender de material-icons hasta que E1.2 publique los
 * oficiales. Los usa también `feature/exportdata`.
 */

private fun icon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
        .build()

internal val AccountBackIcon: ImageVector by lazy {
    icon("arrow_back", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
}

internal val AccountDoneIcon: ImageVector by lazy {
    icon(
        "check_circle",
        "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,17l-5,-5 " +
            "1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z"
    )
}

internal val DeleteForeverIcon: ImageVector by lazy {
    icon(
        "delete",
        "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z"
    )
}

internal val DownloadIcon: ImageVector by lazy {
    icon("download", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z")
}

internal val ShareIcon: ImageVector by lazy {
    icon(
        "share",
        "M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 " +
            "0.09,-0.46 0.09,-0.7s-0.04,-0.47 -0.09,-0.7l7.05,-4.11c0.54,0.5 " +
            "1.25,0.81 2.04,0.81 1.66,0 3,-1.34 3,-3s-1.34,-3 -3,-3 -3,1.34 " +
            "-3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.79,9 6,9c-1.66," +
            "0 -3,1.34 -3,3s1.34,3 3,3c0.79,0 1.5,-0.31 2.04,-0.81l7.12,4.16c-0.05," +
            "0.21 -0.08,0.43 -0.08,0.65 0,1.61 1.31,2.92 2.92,2.92 1.61,0 2.92," +
            "-1.31 2.92,-2.92s-1.31,-2.92 -2.92,-2.92z"
    )
}
