package me.santiagoreina.parcheu.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Valores crudos de color de docs/mockups-inventario.md §3.1 (oscuro) y §3.2 (claro), con los ajustes
 * AA de ADR-0030 en el tema claro. Las pantallas no usan estos valores directamente: leen
 * `MaterialTheme.colorScheme` o `ParcheUTheme.colors`.
 */
object ParcheUPalette {
    // Comunes a ambos temas.
    val Violet = Color(0xFF6D4AFF)
    val Ink = Color(0xFF14111F)
    val White = Color(0xFFFFFFFF)
    val Mint = Color(0xFF2EC4A6)
    val Coral = Color(0xFFFF5C7A)
    val ElectricBlue = Color(0xFF3A86FF)
    val Orange = Color(0xFFFF8A3D)
    val OrangeTint = Color(0xFFFFA266)

    /** Parada intermedia del degradado del paywall. */
    val PaywallMid = Color(0xFF8E5BD9)

    // Tema oscuro (§3.1).
    val DarkSurface1 = Color(0xFF1F1B2E)
    val DarkSurface2 = Color(0xFF2B2641)
    val DarkOutline = Color(0xFF3A3354)
    val DarkText = Color(0xFFF5F2FB)
    val DarkMuted = Color(0xFFB0A9C6)
    val DarkPrimaryTint = Color(0xFFB6A6FF)
    val DarkPrimaryContainer = Color(0xFF2E2566)
    val DarkOnPrimaryContainer = Color(0xFFE0D9FF)
    val DarkMintTint = Color(0xFF5FD9C0)
    val DarkCoralTint = Color(0xFFFF8DA2)
    val DarkBlueTint = Color(0xFF82B1FF)

    /** rgba(5,3,12,.62). */
    val DarkScrim = Color(red = 5, green = 3, blue = 12, alpha = 158)

    /**
     * Mascotas apagadas del cupo y detalles tenues sobre el bloque héroe violeta (dirección A "Parche
     * Vivo"). Es decorativo: la información la lleva la etiqueta accesible, no este color.
     */
    val VioletQuiet = Color(0xFF8F75FF)

    // Contenedores tonales de modo en oscuro (dirección A): fondo de chip grande, tile o banner del modo.
    val DarkMintContainer = Color(0xFF12352E)
    val DarkCoralContainer = Color(0xFF3C1520)
    val DarkBlueContainer = Color(0xFF142A4D)
    val DarkOrangeContainer = Color(0xFF3A2A12)

    // Tema claro de la dirección A "Parche Vivo": lila muy pálido para que el blanco de las tarjetas
    // despegue (antes "Hueso" #FAF7F2 / #F1ECE4 / #E2DBCF, §3.2, que se veía lavado).
    val LilacBg = Color(0xFFF4F2F8)
    val LightSurface1 = Color(0xFFFFFFFF)
    val LightSurface2 = Color(0xFFECE9F3)
    val LightOutline = Color(0xFFE4DFF0)

    // Contenedores tonales de modo en claro.
    val LightMintContainer = Color(0xFFD9F3EE)
    val LightCoralContainer = Color(0xFFFFE2E7)
    val LightBlueContainer = Color(0xFFE0ECFF)
    val LightOrangeContainer = Color(0xFFFFF0DC)

    /** Texto de Pareja sobre su contenedor claro (#C42C4A daría 4,54:1, demasiado justo). */
    val LightCoralOnContainer = Color(0xFFA3203A)
    val LightText = Color(0xFF1A1626)
    val LightMuted = Color(0xFF5C566E)
    val LightPrimaryTint = Color(0xFF5533E8)
    val LightPrimaryContainer = Color(0xFFECE7FF)
    val LightOnPrimaryContainer = Color(0xFF2E1A8C)

    /** ADR-0030: el inventario dice #0E7A65, que no llega a 4,5:1 sobre s2 ni prc. */
    val LightMintText = Color(0xFF0B6B59)
    val LightCoralText = Color(0xFFC42C4A)
    val LightBlueText = Color(0xFF1F5FD1)

    /** ADR-0030: el inventario dice #B8560F, que no llega a 4,5:1 sobre bg, s2 ni prc. */
    val LightOrangeText = Color(0xFF9E480B)

    /** rgba(20,17,31,.45). */
    val LightScrim = Color(red = 20, green = 17, blue = 31, alpha = 115)
}

/**
 * Color pleno de un modo, su tint de texto por tema y el color del contenido sobre el pleno. Con la
 * dirección A ("Parche Vivo") el pleno es también el lomo de las tarjetas (`ModeSpineCard`) y suma un
 * contenedor tonal ([container]) con su texto ([onContainer]), ambos AA.
 */
@Immutable
data class ModeColors(
    val solid: Color,
    val text: Color,
    val onSolid: Color,
    val container: Color,
    val onContainer: Color
)

/**
 * Tokens de color de ParcheU que Material 3 no cubre. Los nombres cortos siguen el inventario
 * (bg, s1, s2, ln, tx, mu, prt, prc, onprc) para que la revisión lado a lado con los mockups sea directa.
 */
@Immutable
data class ParcheUColors(
    val isDark: Boolean,
    val primary: Color,
    val onPrimary: Color,
    val bg: Color,
    val s1: Color,
    val s2: Color,
    val ln: Color,
    val tx: Color,
    val mu: Color,
    val prt: Color,
    val prc: Color,
    val onprc: Color,
    val friendship: ModeColors,
    val couple: ModeColors,
    val study: ModeColors,
    /** Naranja: solo la U del logo, ParcheU+ y celebraciones. */
    val accent: ModeColors,
    /**
     * Acciones destructivas (Reportar, Bloquear, Borrar). Hoy tiene el mismo valor visual que el texto
     * de Pareja (coralt), pero es un token aparte para poder cambiarlo sin tocar el modo
     * (docs/arquitectura.md, diferencias aceptadas #4).
     */
    val error: Color,
    val onError: Color,
    /** Texto e iconos sobre colores plenos de modo y sobre los rasgos del gemelo (#14111F). */
    val onMode: Color,
    val white: Color,
    /** Scrim de sheets y diálogos, ya con su alpha. */
    val scrim: Color,
    /** Color de la palabra "Parche" del wordmark (blanco en oscuro, violeta en claro). */
    val wordmark: Color,
    /**
     * Bloque héroe de la dirección A (`HeroBlock`): relleno violeta pleno en ambos temas, con [onHero]
     * encima (5,15:1) y [heroQuiet] para lo apagado (mascotas del cupo ya gastadas; solo decorativo).
     */
    val hero: Color,
    val onHero: Color,
    val heroQuiet: Color,
    val plusGradient: ParcheUGradient,
    val paywallHeroGradient: ParcheUGradient,
    val celebrationGradient: ParcheURadialGradient
) {
    /** Atajos con los nombres del inventario. */
    val mintt: Color get() = friendship.text
    val coralt: Color get() = couple.text
    val bluet: Color get() = study.text
    val ort: Color get() = accent.text
}

private val PlusGradient =
    ParcheUGradient(
        angleDegrees = 135f,
        stops = listOf(0f to ParcheUPalette.Violet, 1f to ParcheUPalette.Orange)
    )

private val PaywallHeroGradient =
    ParcheUGradient(
        angleDegrees = 160f,
        stops =
        listOf(
            0f to ParcheUPalette.Violet,
            0.55f to ParcheUPalette.PaywallMid,
            1.30f to ParcheUPalette.Orange
        )
    )

/** radial rgba(255,138,61,.20) a rgba(109,74,255,.17) a transparente (B5, G3). */
private val CelebrationGradient =
    ParcheURadialGradient(
        stops =
        listOf(
            0f to ParcheUPalette.Orange.copy(alpha = 0.20f),
            0.5f to ParcheUPalette.Violet.copy(alpha = 0.17f),
            1f to Color.Transparent
        )
    )

val DarkParcheUColors =
    ParcheUColors(
        isDark = true,
        primary = ParcheUPalette.Violet,
        onPrimary = ParcheUPalette.White,
        bg = ParcheUPalette.Ink,
        s1 = ParcheUPalette.DarkSurface1,
        s2 = ParcheUPalette.DarkSurface2,
        ln = ParcheUPalette.DarkOutline,
        tx = ParcheUPalette.DarkText,
        mu = ParcheUPalette.DarkMuted,
        prt = ParcheUPalette.DarkPrimaryTint,
        prc = ParcheUPalette.DarkPrimaryContainer,
        onprc = ParcheUPalette.DarkOnPrimaryContainer,
        friendship = ModeColors(
            solid = ParcheUPalette.Mint,
            text = ParcheUPalette.DarkMintTint,
            onSolid = ParcheUPalette.Ink,
            container = ParcheUPalette.DarkMintContainer,
            onContainer = ParcheUPalette.DarkMintTint
        ),
        couple = ModeColors(
            solid = ParcheUPalette.Coral,
            text = ParcheUPalette.DarkCoralTint,
            onSolid = ParcheUPalette.Ink,
            container = ParcheUPalette.DarkCoralContainer,
            onContainer = ParcheUPalette.DarkCoralTint
        ),
        study = ModeColors(
            solid = ParcheUPalette.ElectricBlue,
            text = ParcheUPalette.DarkBlueTint,
            onSolid = ParcheUPalette.Ink,
            container = ParcheUPalette.DarkBlueContainer,
            onContainer = ParcheUPalette.DarkBlueTint
        ),
        accent = ModeColors(
            solid = ParcheUPalette.Orange,
            text = ParcheUPalette.OrangeTint,
            onSolid = ParcheUPalette.Ink,
            container = ParcheUPalette.DarkOrangeContainer,
            onContainer = ParcheUPalette.OrangeTint
        ),
        error = ParcheUPalette.DarkCoralTint,
        onError = ParcheUPalette.Ink,
        onMode = ParcheUPalette.Ink,
        white = ParcheUPalette.White,
        scrim = ParcheUPalette.DarkScrim,
        wordmark = ParcheUPalette.White,
        hero = ParcheUPalette.Violet,
        onHero = ParcheUPalette.White,
        heroQuiet = ParcheUPalette.VioletQuiet,
        plusGradient = PlusGradient,
        paywallHeroGradient = PaywallHeroGradient,
        celebrationGradient = CelebrationGradient
    )

val LightParcheUColors =
    ParcheUColors(
        isDark = false,
        primary = ParcheUPalette.Violet,
        onPrimary = ParcheUPalette.White,
        bg = ParcheUPalette.LilacBg,
        s1 = ParcheUPalette.LightSurface1,
        s2 = ParcheUPalette.LightSurface2,
        ln = ParcheUPalette.LightOutline,
        tx = ParcheUPalette.LightText,
        mu = ParcheUPalette.LightMuted,
        prt = ParcheUPalette.LightPrimaryTint,
        prc = ParcheUPalette.LightPrimaryContainer,
        onprc = ParcheUPalette.LightOnPrimaryContainer,
        friendship = ModeColors(
            solid = ParcheUPalette.Mint,
            text = ParcheUPalette.LightMintText,
            onSolid = ParcheUPalette.Ink,
            container = ParcheUPalette.LightMintContainer,
            onContainer = ParcheUPalette.LightMintText
        ),
        couple = ModeColors(
            solid = ParcheUPalette.Coral,
            text = ParcheUPalette.LightCoralText,
            onSolid = ParcheUPalette.Ink,
            container = ParcheUPalette.LightCoralContainer,
            onContainer = ParcheUPalette.LightCoralOnContainer
        ),
        study = ModeColors(
            solid = ParcheUPalette.ElectricBlue,
            text = ParcheUPalette.LightBlueText,
            onSolid = ParcheUPalette.Ink,
            container = ParcheUPalette.LightBlueContainer,
            onContainer = ParcheUPalette.LightBlueText
        ),
        accent = ModeColors(
            solid = ParcheUPalette.Orange,
            text = ParcheUPalette.LightOrangeText,
            onSolid = ParcheUPalette.Ink,
            container = ParcheUPalette.LightOrangeContainer,
            onContainer = ParcheUPalette.LightOrangeText
        ),
        error = ParcheUPalette.LightCoralText,
        onError = ParcheUPalette.White,
        onMode = ParcheUPalette.Ink,
        white = ParcheUPalette.White,
        scrim = ParcheUPalette.LightScrim,
        wordmark = ParcheUPalette.Violet,
        hero = ParcheUPalette.Violet,
        onHero = ParcheUPalette.White,
        heroQuiet = ParcheUPalette.VioletQuiet,
        plusGradient = PlusGradient,
        paywallHeroGradient = PaywallHeroGradient,
        celebrationGradient = CelebrationGradient
    )

/**
 * Esquema M3 a partir de los tokens. Sin color dinámico: la marca no cambia con el fondo de pantalla.
 *
 * Advertencia sobre `primary`: es el violeta #6D4AFF y solo sirve como RELLENO (blanco encima = 5,15:1).
 * Como TEXTO no llega a AA (2,80:1 sobre s2 en oscuro). Los componentes M3 por defecto lo usan como
 * contentColor (TextButton, OutlinedButton, etiqueta enfocada de TextField, botones de AlertDialog,
 * tabs), así que en :designsystem esos componentes deben fijar `ParcheUTheme.colors.prt` como color de
 * contenido (E1.3). `ParcheUTheme.colors.prt` es el color de texto de marca; un test bloquea el uso de
 * `colorScheme.primary` como color de texto en :designsystem.
 */
fun ParcheUColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = prc,
        onPrimaryContainer = onprc,
        // Snackbar pinta su acción con inversePrimary sobre inverseSurface (tx): debe ser el prt del tema
        // opuesto (#5533E8 en oscuro, #B6A6FF en claro) para llegar a AA.
        inversePrimary =
        if (isDark) ParcheUPalette.LightPrimaryTint else ParcheUPalette.DarkPrimaryTint,
        secondary = prt,
        onSecondary = if (isDark) bg else white,
        secondaryContainer = prc,
        onSecondaryContainer = onprc,
        // prt: texto y enlaces sobre las superficies (el naranja de acento no es un rol de M3 a propósito:
        // solo se usa en la U del logo, ParcheU+ y celebraciones, siempre de forma explícita).
        tertiary = prt,
        onTertiary = if (isDark) bg else white,
        tertiaryContainer = prc,
        onTertiaryContainer = onprc,
        background = bg,
        onBackground = tx,
        surface = bg,
        onSurface = tx,
        surfaceVariant = s2,
        onSurfaceVariant = mu,
        surfaceTint = primary,
        inverseSurface = tx,
        inverseOnSurface = bg,
        error = error,
        onError = onError,
        errorContainer = s2,
        onErrorContainer = error,
        outline = ln,
        outlineVariant = ln,
        scrim = scrim.copy(alpha = 1f),
        surfaceBright = s2,
        surfaceDim = bg,
        surfaceContainerLowest = bg,
        surfaceContainerLow = s1,
        surfaceContainer = s1,
        surfaceContainerHigh = s2,
        surfaceContainerHighest = s2
    )
}
