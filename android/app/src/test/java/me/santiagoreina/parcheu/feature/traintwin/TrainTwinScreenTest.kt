package me.santiagoreina.parcheu.feature.traintwin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class TrainTwinScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val full =
        TrainTwinUiState.Loaded(
            TrainTwinContent(
                pending = listOf(TrainTwinSamples.pending),
                accepted = listOf(TrainTwinSamples.accepted),
                prompts = listOf(TrainTwinSamples.control, TrainTwinSamples.rating)
            )
        )

    private fun show(
        state: TrainTwinUiState,
        callbacks: TrainTwinCallbacks = TrainTwinCallbacks()
    ) = composeRule.setContent {
        ParcheUTheme(darkTheme = true) { TrainTwinScreen(state, callbacks) }
    }

    @Test
    fun showsPendingAcceptedAndRateableContent() {
        show(full)
        composeRule.onNodeWithText(TrainTwinSamples.pending.text).assertIsDisplayed()
        composeRule.onNodeWithTag(
            TrainTwinTags.accepted("m-2")
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "“${TrainTwinSamples.rating.text}”"
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(TrainTwinTags.CONTROL).assertIsDisplayed()
    }

    @Test
    fun actionsInvokeCallbacksWithTheirIds() {
        val calls = mutableListOf<String>()
        show(
            full,
            TrainTwinCallbacks(
                onAccept = { calls += "accept:${it.id}" },
                onReject = { calls += "reject:${it.id}" },
                onRate = { id, up -> calls += "rate:$id:$up" },
                onControlAnswer = { id, i -> calls += "control:$id:$i" },
                onReport = { calls += "report:$it" }
            )
        )
        composeRule.onNodeWithTag(TrainTwinTags.controlOption(1)).performScrollTo().performClick()
        composeRule.onNodeWithTag(TrainTwinTags.accept("m-1")).performScrollTo().performClick()
        composeRule.onNodeWithTag(TrainTwinTags.reject("m-1")).performScrollTo().performClick()
        composeRule.onNodeWithTag(TrainTwinTags.rateUp("sim-1:2")).performScrollTo().performClick()
        composeRule.onNodeWithTag(
            TrainTwinTags.rateDown("sim-1:2")
        ).performScrollTo().performClick()
        composeRule.onNodeWithTag(TrainTwinTags.report("sim-1:2")).performScrollTo().performClick()
        assertEquals(
            listOf(
                "control:c-1:1",
                "accept:m-1",
                "reject:m-1",
                "rate:sim-1:2:true",
                "rate:sim-1:2:false",
                "report:sim-1:2"
            ),
            calls
        )
    }

    @Test
    fun emptyStatesExplainWhatWillAppear() {
        show(TrainTwinUiState.Loaded(TrainTwinContent()))
        composeRule.onNodeWithTag(TrainTwinTags.PENDING_EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(TrainTwinTags.RATINGS_EMPTY).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun errorOffersRetry() {
        var retried = false
        show(
            TrainTwinUiState.Error(AppError.Network),
            TrainTwinCallbacks(onRetry = {
                retried = true
            })
        )
        composeRule.onNodeWithTag(TrainTwinTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithText("Reintentar").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun backInvokesCallback() {
        var back = false
        show(full, TrainTwinCallbacks(onBack = { back = true }))
        composeRule.onNodeWithContentDescription("Volver").performClick()
        assertEquals(true, back)
    }
}
