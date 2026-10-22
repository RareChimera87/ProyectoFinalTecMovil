package me.santiagoreina.parcheu.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    @Test
    fun blackOnWhiteIsTwentyOne() {
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01)
    }

    /**
     * Decisión del inventario §4 y ADR-0030: en Estudio el texto sobre el azul pleno es #14111F, porque
     * el blanco no llega a AA.
     */
    @Test
    fun inkOnStudyBluePassesAndWhiteDoesNot() {
        val ink = contrastRatio(ParcheUPalette.Ink, ParcheUPalette.ElectricBlue)
        val white = contrastRatio(ParcheUPalette.White, ParcheUPalette.ElectricBlue)
        assertTrue("tinta sobre azul = $ink", ink >= Wcag.AA_NORMAL_TEXT)
        assertEquals(5.3, ink, 0.1)
        assertTrue("blanco sobre azul = $white", white < Wcag.AA_NORMAL_TEXT)
    }

    @Test
    fun everyDeclaredPairPassesAaInBothThemes() {
        val failures =
            listOf(
                "oscuro" to DarkParcheUColors,
                "claro" to LightParcheUColors
            ).flatMap { (theme, colors) ->
                colors.declaredTextPairs()
                    .map { pair ->
                        Triple(theme, pair, contrastRatio(pair.foreground, pair.background))
                    }
                    .filter { (_, pair, ratio) -> ratio < pair.minRatio }
                    .map { (t, pair, ratio) ->
                        "$t ${pair.name}: ${"%.2f".format(ratio)} < ${pair.minRatio}"
                    }
            }
        assertTrue("Pares sin AA:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun declaredPairsCoverModeTextOnLightSurfaces() {
        val names = LightParcheUColors.declaredTextPairs().map { it.name }.toSet()
        for (text in listOf("ort", "mintt", "coralt", "bluet")) {
            for (surface in listOf("bg", "s1", "s2", "prc")) {
                assertTrue("falta $text/$surface", "$text/$surface" in names)
            }
        }
    }

    /** ADR-0030: los hex del inventario fallan y por eso se corrigen. */
    @Test
    fun lightThemeUsesCorrectedOrangeAndMint() {
        assertEquals(Color(0xFF9E480B), LightParcheUColors.ort)
        assertEquals(Color(0xFF0B6B59), LightParcheUColors.mintt)
        val oldOrt = Color(0xFFB8560F)
        val oldMintt = Color(0xFF0E7A65)
        assertTrue(contrastRatio(oldOrt, LightParcheUColors.s2) < Wcag.AA_NORMAL_TEXT)
        assertTrue(contrastRatio(oldMintt, LightParcheUColors.prc) < Wcag.AA_NORMAL_TEXT)
    }

    @Test
    fun errorIsItsOwnTokenWithCoraltValue() {
        assertEquals(DarkParcheUColors.coralt, DarkParcheUColors.error)
        assertEquals(LightParcheUColors.coralt, LightParcheUColors.error)
        assertEquals(LightParcheUColors.error, LightParcheUColors.toColorScheme().error)
    }
}
