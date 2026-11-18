package me.santiagoreina.parcheu.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Radios del inventario §3.6, con los de la dirección A "Parche Vivo" (tarjeta 18, héroe 24, foto 20). */
object ParcheURadius {
    val Checkbox: Dp = 7.dp

    /** 14 desde la dirección A (antes 12): botones compactos de 40 y 36. */
    val ButtonCompact: Dp = 14.dp
    val Field: Dp = 14.dp
    val FieldLarge: Dp = 16.dp
    val Button: Dp = 16.dp
    val NavPill: Dp = 15.dp
    val Card: Dp = 18.dp

    /** Fotos con duotono (B1) y tarjetas de opción con imagen. */
    val Photo: Dp = 20.dp
    val Hero: Dp = 24.dp
    val HeroLarge: Dp = 28.dp
    val Sheet: Dp = 28.dp
    val Progress: Dp = 4.dp
}

/** Formas listas para usar. Chips, pills, avatares y nodos son círculos o píldoras (999 / 50 %). */
@Immutable
data class ParcheUShapeTokens(
    val pill: RoundedCornerShape = CircleShape,
    val avatar: RoundedCornerShape = CircleShape,
    val button: RoundedCornerShape = RoundedCornerShape(ParcheURadius.Button),
    val buttonCompact: RoundedCornerShape = RoundedCornerShape(ParcheURadius.ButtonCompact),
    val card: RoundedCornerShape = RoundedCornerShape(ParcheURadius.Card),
    val hero: RoundedCornerShape = RoundedCornerShape(ParcheURadius.Hero),
    val photo: RoundedCornerShape = RoundedCornerShape(ParcheURadius.Photo),
    val heroLarge: RoundedCornerShape = RoundedCornerShape(ParcheURadius.HeroLarge),
    val bottomSheet: RoundedCornerShape =
        RoundedCornerShape(topStart = ParcheURadius.Sheet, topEnd = ParcheURadius.Sheet),
    val field: RoundedCornerShape = RoundedCornerShape(ParcheURadius.Field),
    val checkbox: RoundedCornerShape = RoundedCornerShape(ParcheURadius.Checkbox),
    val navPill: RoundedCornerShape = RoundedCornerShape(ParcheURadius.NavPill),
    val progress: RoundedCornerShape = RoundedCornerShape(ParcheURadius.Progress)
)

/** Formas de Material 3 alineadas con el inventario (los componentes M3 las toman por defecto). */
val ParcheUMaterialShapes =
    Shapes(
        extraSmall = RoundedCornerShape(ParcheURadius.Checkbox),
        small = RoundedCornerShape(ParcheURadius.ButtonCompact),
        medium = RoundedCornerShape(ParcheURadius.Button),
        large = RoundedCornerShape(ParcheURadius.Card),
        extraLarge = RoundedCornerShape(ParcheURadius.HeroLarge)
    )
