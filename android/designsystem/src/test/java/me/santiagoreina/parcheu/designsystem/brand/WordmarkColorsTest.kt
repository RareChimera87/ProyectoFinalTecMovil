package me.santiagoreina.parcheu.designsystem.brand

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** El color de "Parche" sigue el tema de la app que se le pasa, no el del sistema. */
class WordmarkColorsTest {
    @Test
    fun darkThemeUsesWhite() = assertEquals(Color(0xFFFFFFFF), WordmarkColors.base(darkTheme = true))

    @Test
    fun lightThemeUsesViolet() = assertEquals(Color(0xFF6D4AFF), WordmarkColors.base(darkTheme = false))

    @Test
    fun parcheNeverMatchesTheUOrange() {
        assertNotEquals(WordmarkColors.Orange, WordmarkColors.base(true))
        assertNotEquals(WordmarkColors.Orange, WordmarkColors.base(false))
    }
}
