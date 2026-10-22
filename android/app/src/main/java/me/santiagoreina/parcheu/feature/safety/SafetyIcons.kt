package me.santiagoreina.parcheu.feature.safety

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/*
 * Iconos de H1, H2 y las confirmaciones como vectores propios (24dp, trazados de Material Symbols).
 * Igual que en F9 (E5.2), evitan la dependencia de material-icons hasta que E1.2 publique los oficiales.
 */

private fun icon(name: String, pathData: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
    .build()

internal val MuteIcon: ImageVector by lazy {
    icon(
        "volume_off",
        "M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v2.21l2.45,2.45c0.03,-0.2 0.05,-0.41 0.05,-0.63zM19,12c0,0.94 " +
            "-0.2,1.82 -0.54,2.64l1.51,1.51C20.63,14.91 21,13.5 21,12c0,-4.28 -2.99,-7.86 -7,-8.77v2.06c2.89,0.86 " +
            "5,3.54 5,6.71zM4.27,3L3,4.27 7.73,9H3v6h4l5,5v-6.73l4.25,4.25c-0.67,0.52 -1.42,0.93 " +
            "-2.25,1.18v2.06c1.38," +
            "-0.31 2.63,-0.95 3.69,-1.81L19.73,21 21,19.73l-9,-9L4.27,3zM12,4L9.91,6.09 12,8.18V4z",
    )
}

internal val UnmuteIcon: ImageVector by lazy {
    icon(
        "volume_up",
        "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14," +
            "3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z",
    )
}

internal val FlagIcon: ImageVector by lazy { icon("flag", "M14.4,6L14,4H5v17h2v-7h5.6l0.4,2h7V6z") }

internal val BlockIcon: ImageVector by lazy {
    icon(
        "block",
        "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM4,12c0,-4.42 3.58,-8 8,-8 1.85,0 " +
            "3.55,0.63 4.9,1.69L5.69,16.9C4.63,15.55 4,13.85 4,12zM12,20c-1.85,0 -3.55,-0.63 " +
            "-4.9,-1.69L18.31,7.1C19.37," +
            "8.45 20,10.15 20,12c0,4.42 -3.58,8 -8,8z",
    )
}

internal val ShieldIcon: ImageVector by lazy {
    icon("shield", "M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12V5l-9,-4z")
}

internal val CloseIcon: ImageVector by lazy {
    icon(
        "close",
        "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z",
    )
}

internal val CallIcon: ImageVector by lazy {
    icon(
        "call",
        "M6.62,10.79c1.44,2.83 3.76,5.14 6.59,6.59l2.2,-2.2c0.27,-0.27 0.67,-0.36 1.02,-0.24 1.12,0.37 2.33,0.57 " +
            "3.57,0.57 0.55,0 1,0.45 1,1V20c0,0.55 -0.45,1 -1,1 -9.39,0 -17,-7.61 -17,-17 0,-0.55 " +
            "0.45,-1 1,-1h3.5c0.55," +
            "0 1,0.45 1,1 0,1.25 0.2,2.45 0.57,3.57 0.11,0.35 0.03,0.74 -0.25,1.02l-2.2,2.2z",
    )
}

internal val CheckCircleIcon: ImageVector by lazy {
    icon(
        "check_circle",
        "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,17l-5,-5 1.41,-1.41L10,14.17l7.59," +
            "-7.59L19,8l-9,9z",
    )
}
