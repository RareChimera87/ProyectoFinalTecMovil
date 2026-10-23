package me.santiagoreina.parcheu.designsystem.components.compound

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import me.santiagoreina.parcheu.designsystem.components.base.testIcon
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Criterios de E1.4 sobre LockedOverlay: con `sdk=30` dibuja un scrim opaco y con `sdk=35` aplica blur (dos
 * capturas distintas), y su API solo acepta un placeholder decorativo, nunca datos reales.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-xhdpi")
class LockedOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun Subject() {
        LockedOverlay("Disponible con ParcheU+", "Ver ParcheU+", {
        }, testIcon(), placeholder = LockedPlaceholder.List)
    }

    private fun render(darkTheme: Boolean = true) {
        composeRule.setContent { ParcheUTheme(darkTheme) { Subject() } }
    }

    private fun capture(name: String, darkTheme: Boolean) {
        render(darkTheme)
        val suffix = if (darkTheme) "dark" else "light"
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/compound/locked_overlay_${name}_$suffix.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    @Test
    fun sdk35AplicaBlurYNoScrimOpaco() {
        render()
        composeRule.onNodeWithTag(LOCKED_OVERLAY_BLUR_TAG).assertExists()
        composeRule.onAllNodes(hasTestTag(LOCKED_OVERLAY_SCRIM_TAG)).assertCountEquals(0)
        composeRule.onNodeWithText("Ver ParcheU+").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [30])
    fun sdk30DibujaScrimOpacoYNoBlur() {
        render()
        composeRule.onNodeWithTag(LOCKED_OVERLAY_SCRIM_TAG).assertExists()
        composeRule.onAllNodes(hasTestTag(LOCKED_OVERLAY_BLUR_TAG)).assertCountEquals(0)
        composeRule.onNodeWithText("Ver ParcheU+").assertIsDisplayed()
    }

    @Test fun capturaSdk35Oscuro() = capture("sdk35", true)

    @Test fun capturaSdk35Claro() = capture("sdk35", false)

    @Test
    @Config(sdk = [30])
    fun capturaSdk30Oscuro() = capture("sdk30", true)

    @Test
    @Config(sdk = [30])
    fun capturaSdk30Claro() = capture("sdk30", false)

    @Test
    fun lasCapturasDeSdk30YSdk35SonDistintas() {
        listOf("dark", "light").forEach { theme ->
            val dir = File("src/test/screenshots/compound")
            val blur = File(dir, "locked_overlay_sdk35_$theme.png")
            val scrim = File(dir, "locked_overlay_sdk30_$theme.png")
            if (blur.exists() && scrim.exists()) {
                assertFalse(
                    "Las capturas $theme de sdk30 y sdk35 son idénticas",
                    blur.readBytes().contentEquals(scrim.readBytes())
                )
            }
        }
    }

    @Test
    fun elPlaceholderNoExponeTextoNiDatos() {
        render()
        // Solo existen el título y el botón; el placeholder no aporta nodos de texto.
        composeRule.onAllNodesWithText("Disponible con ParcheU+").assertCountEquals(1)
        composeRule.onAllNodesWithText("Ver ParcheU+").assertCountEquals(1)
    }

    @Test
    fun laApiSoloAceptaUnPlaceholderDecorativo() {
        val method = Class.forName(
            "me.santiagoreina.parcheu.designsystem.components.compound.LockedKt"
        )
            .declaredMethods
            .single { it.name == "LockedOverlay" && it.parameterTypes.size >= 6 }
        val allowed = setOf<Class<*>>(
            String::class.java,
            Function0::class.java,
            Painter::class.java,
            androidx.compose.ui.Modifier::class.java,
            LockedPlaceholder::class.java,
            androidx.compose.runtime.Composer::class.java,
            Int::class.javaPrimitiveType!!
        )
        method.parameterTypes.forEach {
            assertTrue("LockedOverlay no debe aceptar ${it.name}", it in allowed)
        }
        // Ningún parámetro es un slot @Composable (sería Function2<Composer, Int, Unit>) ni una colección.
        assertTrue(method.parameterTypes.none { it == Function2::class.java })
        assertTrue(method.parameterTypes.contains(LockedPlaceholder::class.java))
        assertEquals(4, LockedPlaceholder.values().size)
    }
}
