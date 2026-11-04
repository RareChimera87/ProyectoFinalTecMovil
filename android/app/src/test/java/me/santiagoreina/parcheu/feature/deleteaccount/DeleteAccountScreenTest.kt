package me.santiagoreina.parcheu.feature.deleteaccount

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeleteAccountScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        state: DeleteAccountUiState,
        actions: DeleteAccountActions = DeleteAccountActions()
    ) {
        composeRule.setContent { ParcheUTheme { DeleteAccountScreen(state, actions) } }
    }

    @Test
    fun theButtonStaysOffUntilTheCheckboxIsChecked() {
        var confirmed: Boolean? = null
        show(DeleteAccountUiState(), DeleteAccountActions(onConfirmChange = { confirmed = it }))
        composeRule.onNodeWithText("¿Borrar tu cuenta?").assertIsDisplayed()
        composeRule.onNodeWithTag(DeleteAccountTags.DELETE).assertIsNotEnabled()
        composeRule.onNodeWithTag(DeleteAccountTags.CONFIRM).performClick()
        assertEquals(true, confirmed)
    }

    @Test
    fun confirmedStateEnablesDeleteAndWarnsAboutPlay() {
        var deleted = false
        var play = false
        show(
            DeleteAccountUiState(confirmed = true),
            DeleteAccountActions(onDelete = {
                deleted = true
            }, onManageSubscription = { play = true })
        )
        composeRule.onNodeWithTag(DeleteAccountTags.DELETE).assertIsEnabled().performClick()
        composeRule.onNodeWithText("Ir a Google Play").performScrollTo().performClick()
        assertTrue(deleted)
        assertTrue(play)
    }

    @Test
    fun afterDeletingShowsTheConfirmationAndDone() {
        var done = false
        show(DeleteAccountUiState(deleted = true), DeleteAccountActions(onDone = { done = true }))
        composeRule.onNodeWithText("Borramos tu cuenta").assertIsDisplayed()
        composeRule.onNodeWithTag(DeleteAccountTags.DONE).performClick()
        assertTrue(done)
    }

    @Test
    fun theConfirmationIsOneCheckboxNodeThatTheLabelAlsoToggles() {
        var confirmed: Boolean? = null
        show(DeleteAccountUiState(), DeleteAccountActions(onConfirmChange = { confirmed = it }))
        // Un solo nodo con la frase (no se lee dos veces), con rol de casilla y estado.
        composeRule.onAllNodesWithText(CONFIRM_LABEL, useUnmergedTree = false).assertCountEquals(1)
        composeRule.onNodeWithTag(DeleteAccountTags.CONFIRM)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ToggleableState,
                    ToggleableState.Off
                )
            )
        // Tocar el texto marca la casilla.
        composeRule.onNodeWithText(CONFIRM_LABEL).performClick()
        assertEquals(true, confirmed)
    }

    @Test
    fun theErrorIsAnnouncedAsAPoliteLiveRegion() {
        show(DeleteAccountUiState(confirmed = true, error = AppError.Network))
        composeRule.onNodeWithTag(DeleteAccountTags.ERROR)
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
    }
}
