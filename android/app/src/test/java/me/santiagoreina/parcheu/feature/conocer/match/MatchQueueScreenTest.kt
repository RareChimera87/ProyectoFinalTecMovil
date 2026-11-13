package me.santiagoreina.parcheu.feature.conocer.match

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Usage
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class MatchQueueScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(quota: Usage?) {
        composeRule.setContent {
            ParcheUTheme {
                MatchQueueScreen(
                    state = MatchQueueUiState(mode = Mode.AMISTAD, dailyQuota = quota),
                    onModeSelected = {},
                    onCancel = {},
                    onRetry = {},
                    onOpenMatch = {},
                    animate = false
                )
            }
        }
    }

    private val resetsAt = Instant.fromEpochSeconds(0)

    @Test
    fun freeAccountShowsItsRealLimit() {
        show(Usage(used = 0, limit = 1, resetsAt = resetsAt))

        composeRule.onNodeWithTag(MatchQueueTags.TODAY_COUNT).assertTextEquals("1 de 1")
    }

    @Test
    fun plusAccountDoesNotShowAFixedOneOfOne() {
        show(Usage(used = 3, limit = null, resetsAt = resetsAt))

        composeRule.onNodeWithTag(MatchQueueTags.TODAY_COUNT).assertIsDisplayed()
            .assertTextEquals(MatchStrings.TODAY_UNLIMITED)
    }

    @Test
    fun withoutQuotaDataTheCountLineIsOmitted() {
        show(null)

        composeRule.onNodeWithTag(MatchQueueTags.TODAY_COUNT).assertDoesNotExist()
    }
}
