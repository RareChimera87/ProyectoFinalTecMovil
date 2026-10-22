package me.santiagoreina.parcheu.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarColorsTest {
    private val alphas = listOf(0.22f, 0.5f, 0.8f, 0.85f, 0.9f, 1.0f)

    @Test
    fun initialsHaveAaContrastForEveryAlphaThemeAndSurface() {
        val failures = mutableListOf<String>()
        for ((theme, colors) in listOf(
            "oscuro" to DarkParcheUColors,
            "claro" to LightParcheUColors,
        )) {
            for ((surfaceName, surface) in listOf(
                "bg" to colors.bg,
                "s1" to colors.s1,
                "s2" to colors.s2,
            )) {
                for (alpha in alphas) {
                    val avatar = avatarColors(alpha, colors.isDark)
                    val ratio = contrastRatio(avatar.content, avatar.fill, backdrop = surface)
                    if (ratio < Wcag.AA_NORMAL_TEXT) {
                        failures += "$theme/$surfaceName alpha=$alpha: ${"%.2f".format(ratio)}"
                    }
                }
            }
        }
        assertTrue("Avatares sin AA:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun darkThemeAlwaysUsesWhiteAndKeepsAlpha() {
        for (alpha in alphas) {
            val avatar = avatarColors(alpha, darkTheme = true)
            assertEquals(ParcheUPalette.White, avatar.content)
            assertEquals(alpha, avatar.fillAlpha, 1e-6f)
        }
    }

    @Test
    fun lightThemeSwitchesToSolidFillAboveEightyPercent() {
        val atLimit = avatarColors(0.80f, darkTheme = false)
        assertEquals(ParcheUPalette.Ink, atLimit.content)
        assertEquals(0.80f, atLimit.fillAlpha, 1e-6f)

        val above = avatarColors(0.85f, darkTheme = false)
        assertEquals(ParcheUPalette.White, above.content)
        assertEquals(1f, above.fillAlpha, 1e-6f)
        assertEquals(5.15, contrastRatio(above.content, above.fill), 0.01)
    }
}
