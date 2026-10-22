package me.santiagoreina.parcheu.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Umbrales WCAG 2.x nivel AA. */
object Wcag {
    const val AA_NORMAL_TEXT = 4.5

    /** Texto grande: ≥ 18sp normal o ≥ 14sp en negrita. */
    const val AA_LARGE_TEXT = 3.0
}

/** Luminancia relativa WCAG de un color opaco (los canales de Compose ya vienen en sRGB). */
fun Color.relativeLuminance(): Double {
    fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
}

/**
 * Relación de contraste WCAG entre [foreground] y [background]. Si alguno es translúcido se compone
 * primero: el fondo sobre [backdrop] y el texto sobre el fondo resultante.
 */
fun contrastRatio(foreground: Color, background: Color, backdrop: Color = Color.Black): Double {
    val bg = if (background.alpha < 1f) background.compositeOver(backdrop) else background
    val fg = if (foreground.alpha < 1f) foreground.compositeOver(bg) else foreground
    val l1 = fg.relativeLuminance()
    val l2 = bg.relativeLuminance()
    return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
}

/** Un par texto/fondo declarado por el sistema de diseño y el contraste mínimo que debe cumplir. */
data class TextColorPair(
    val name: String,
    val foreground: Color,
    val background: Color,
    val minRatio: Double = Wcag.AA_NORMAL_TEXT,
)

/**
 * Todos los pares texto/fondo que el sistema de diseño declara para un tema (ADR-0030). Un componente
 * nuevo que combine texto y fondo de otra forma debe agregar su par aquí para que lo cubra el test de AA.
 * Todos se exigen a 4,5:1 (texto normal); ninguno depende del umbral de texto grande.
 */
fun ParcheUColors.declaredTextPairs(): List<TextColorPair> {
    val surfaces = listOf("bg" to bg, "s1" to s1, "s2" to s2, "prc" to prc)
    val texts =
        listOf(
            "tx" to tx,
            "mu" to mu,
            "prt" to prt,
            "mintt" to mintt,
            "coralt" to coralt,
            "bluet" to bluet,
            "ort" to ort,
            "error" to error,
        )
    val onSurfaces =
        texts.flatMap { (textName, text) ->
            surfaces.map { (surfaceName, surface) ->
                TextColorPair("$textName/$surfaceName", text, surface)
            }
        }
    val onFills =
        listOf(
            TextColorPair("onprc/prc", onprc, prc),
            TextColorPair("onPrimary/primary", onPrimary, primary),
            TextColorPair("onError/error", onError, error),
            TextColorPair("onMode/friendship", friendship.onSolid, friendship.solid),
            TextColorPair("onMode/couple", couple.onSolid, couple.solid),
            TextColorPair("onMode/study", study.onSolid, study.solid),
            TextColorPair("onMode/accent", accent.onSolid, accent.solid),
            TextColorPair("wordmark/bg", wordmark, bg),
        )
    return onSurfaces + onFills
}

/**
 * Pares texto/fondo que el esquema M3 de [toColorScheme] declara para componentes por defecto
 * (Snackbar, texto secundario). `primary` como texto queda fuera a propósito: ver KDoc de [toColorScheme].
 */
fun ParcheUColors.declaredSchemeTextPairs(): List<TextColorPair> {
    val scheme = toColorScheme()
    return listOf(
        TextColorPair(
            "inversePrimary/inverseSurface",
            scheme.inversePrimary,
            scheme.inverseSurface,
        ),
        TextColorPair(
            "inverseOnSurface/inverseSurface",
            scheme.inverseOnSurface,
            scheme.inverseSurface,
        ),
        TextColorPair(
            "onSurfaceVariant/surfaceContainerHigh",
            scheme.onSurfaceVariant,
            scheme.surfaceContainerHigh,
        ),
        TextColorPair(
            "secondary/surfaceContainerHigh",
            scheme.secondary,
            scheme.surfaceContainerHigh,
        ),
        TextColorPair(
            "tertiary/surfaceContainerHigh",
            scheme.tertiary,
            scheme.surfaceContainerHigh,
        ),
        TextColorPair(
            "onErrorContainer/errorContainer",
            scheme.onErrorContainer,
            scheme.errorContainer,
        ),
    )
}
