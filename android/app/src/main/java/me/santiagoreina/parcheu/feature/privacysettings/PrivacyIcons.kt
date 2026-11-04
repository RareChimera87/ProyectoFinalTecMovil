package me.santiagoreina.parcheu.feature.privacysettings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/*
 * Iconos de H3 (E8.2) como vectores propios de 24dp con los trazados de Material Symbols, igual que B2 y
 * H1/H2: sin depender de material-icons hasta que E1.2 publique los oficiales.
 */

private fun icon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
        .build()

internal val LockIcon: ImageVector by lazy {
    icon(
        "lock",
        "M18,8h-1L17,6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2L6,8c-1.1,0 -2,0.9 -2,2v10c0,1.1 " +
            "0.9,2 2,2h12c1.1,0 2,-0.9 2,-2L20,10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 " +
            "-2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8L8.9,8L8.9,6c0,-1.71 1.39,-3.1 " +
            "3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z"
    )
}

internal val LogoutIcon: ImageVector by lazy {
    icon(
        "logout",
        "M17,7l-1.41,1.41L18.17,11H8v2h10.17l-2.58,2.58L17,17l5,-5zM4,5h8V3H4c-1.1,0 -2,0.9 " +
            "-2,2v14c0,1.1 0.9,2 2,2h8v-2H4V5z"
    )
}

internal val TermsIcon: ImageVector by lazy {
    icon(
        "description",
        "M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6," +
            "-6zM16,18H8v-2h8v2zM16,14H8v-2h8v2zM13,9V3.5L18.5,9H13z"
    )
}

internal val PrivacyTipIcon: ImageVector by lazy {
    icon(
        "privacy_tip",
        "M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12V5l-9,-4zM11,7h2v2h-2V7z" +
            "M11,11h2v6h-2v-6z"
    )
}

internal val ChevronIcon: ImageVector by lazy {
    icon("chevron_right", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z")
}
