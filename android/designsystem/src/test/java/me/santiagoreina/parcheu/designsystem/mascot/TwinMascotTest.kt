package me.santiagoreina.parcheu.designsystem.mascot

import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TwinMascotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun heightIs1125TimesWidthForEverySize() {
        TwinMascotSize.entries.forEach { s ->
            assertEquals(s.width.value * 1.125f, s.height.value, 1e-3f)
        }
        assertEquals(listOf(30f, 56f, 80f, 116f), TwinMascotSize.entries.map { it.width.value })
    }

    @Test
    fun renderedBoundsKeepTheRatio() {
        val current = mutableStateOf(TwinMascotSize.S)
        composeRule.setContent {
            ParcheUTheme {
                TwinMascot(Modifier.testTag("m"), size = current.value, animationsEnabled = false)
            }
        }
        TwinMascotSize.entries.forEach { s ->
            current.value = s
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(
                "m",
            ).assertWidthIsEqualTo(s.width).assertHeightIsEqualTo(s.height)
        }
    }

    @Test
    fun animationScaleZeroKeepsMascotStill() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val frames = captureTalkingFrames()
        assertTrue("con escala 0 la mascota no debe cambiar", frames.first == frames.second)
    }

    @Test
    fun animationScaleOneAnimatesMascot() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val frames = captureTalkingFrames()
        assertTrue("con escala 1 la mascota debe animarse", frames.first != frames.second)
    }

    @Test
    fun errorGrayIsComputedInSrgbNotLinear() {
        val gray = androidx.compose.ui.graphics.Color(
            0x6D / 255f,
            0x4A / 255f,
            0xFF / 255f,
        ).toGray()
        // CSS grayscale(1) del violeta #6D4AFF da cerca de #5E5E5E (no #272727).
        assertEquals(0.37f, gray.red, 0.02f)
        assertEquals(gray.red, gray.green, 1e-6f)
        assertEquals(gray.red, gray.blue, 1e-6f)
    }

    private fun captureTalkingFrames(): Pair<Float, Float> {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ParcheUTheme {
                TwinMascot(Modifier.testTag("m"), size = TwinMascotSize.XL, talking = true)
            }
        }
        composeRule.mainClock.advanceTimeBy(50)
        val first = phase()
        composeRule.mainClock.advanceTimeBy(200)
        return first to phase()
    }

    private fun phase(): Float = composeRule.onNodeWithTag("m").fetchSemanticsNode().config[MascotPhaseKey]

    @Test
    fun animationsEnabledByDefault() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val enabled = mutableStateOf<Boolean?>(null)
        composeRule.setContent { ParcheUTheme { enabled.value = rememberAnimationsEnabled() } }
        composeRule.waitForIdle()
        assertTrue(enabled.value!!)
    }
}
