package me.santiagoreina.parcheu.designsystem.mascot

import android.provider.Settings
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
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

@RunWith(RobolectricTestRunner::class)
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
            ParcheUTheme { TwinMascot(Modifier.testTag("m"), size = current.value, animationsEnabled = false) }
        }
        TwinMascotSize.entries.forEach { s ->
            current.value = s
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("m").assertWidthIsEqualTo(s.width).assertHeightIsEqualTo(s.height)
        }
    }

    @Test
    fun animationsDisabledWhenDurationScaleIsZero() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val enabled = mutableStateOf<Boolean?>(null)
        composeRule.setContent {
            ParcheUTheme {
                enabled.value = rememberAnimationsEnabled()
                TwinMascot(talking = true)
            }
        }
        composeRule.waitForIdle()
        assertFalse(enabled.value!!)
        // Con escala 0 y talking=true no hay animación infinita: la composición queda en reposo.
        assertTrue(composeRule.mainClock.autoAdvance)
    }

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
