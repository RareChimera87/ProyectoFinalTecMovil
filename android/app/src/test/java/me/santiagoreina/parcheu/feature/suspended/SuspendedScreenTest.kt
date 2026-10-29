package me.santiagoreina.parcheu.feature.suspended

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SuspendedScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun offersExportDeleteAndSignOut() {
        val clicks = mutableListOf<String>()
        composeRule.setContent {
            ParcheUTheme {
                SuspendedScreen(
                    onExportData = { clicks += "export" },
                    onDeleteAccount = { clicks += "delete" },
                    onSignOut = { clicks += "signOut" }
                )
            }
        }
        composeRule.onNodeWithText("Tu cuenta está suspendida").assertExists()
        composeRule.onNodeWithTag(SuspendedTags.EXPORT).assertIsEnabled().performClick()
        composeRule.onNodeWithTag(SuspendedTags.DELETE).assertIsEnabled().performClick()
        composeRule.onNodeWithTag(SuspendedTags.SIGN_OUT).assertIsEnabled().performClick()
        assertEquals(listOf("export", "delete", "signOut"), clicks)
    }
}
