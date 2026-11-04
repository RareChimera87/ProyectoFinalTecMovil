package me.santiagoreina.parcheu.feature.modes

import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Criterio de UI de E2.5: 0 modos deshabilita Continuar; encender Pareja abre B3; cancelar lo deja apagado. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModesScreenTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val repo = FakeModesRepository()
    private val viewModel = ModesViewModel(repo) { TODAY }

    private fun show() {
        composeRule.setContent {
            ParcheUTheme(darkTheme = true) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                ModesScreen(
                    state = state,
                    showBack = false,
                    onBack = {},
                    onToggle = viewModel::onToggle,
                    onContinue = viewModel::onContinue,
                    onRetry = viewModel::load,
                    onDialogConfirmedChange = viewModel::onDialogConfirmedChange,
                    onDialogConfirm = viewModel::onDialogConfirm,
                    onDialogCancel = viewModel::onDialogCancel,
                    animateLoading = false
                )
            }
        }
    }

    @Test
    fun continueIsDisabledWithZeroModesAndEnabledWithOne() {
        show()
        composeRule.onNodeWithTag(ModesTags.CONTINUE).assertIsNotEnabled()
        composeRule.onNodeWithTag(ModesTags.switch(Mode.AMISTAD)).performClick()
        composeRule.onNodeWithTag(ModesTags.CONTINUE).assertIsEnabled()
        composeRule.onNodeWithTag(ModesTags.switch(Mode.AMISTAD)).performClick()
        composeRule.onNodeWithTag(ModesTags.CONTINUE).assertIsNotEnabled()
    }

    @Test
    fun turningOnParejaOpensB3AndCancelLeavesItOff() {
        show()
        composeRule.onNodeWithTag(ModesTags.switch(Mode.PAREJA)).performClick()
        composeRule.onNodeWithTag(ModesTags.DIALOG, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(
            ModesTags.DIALOG_DATE,
            useUnmergedTree = true
        ).assertTextContains("14 / 03 / 2005", substring = true)
        composeRule.onNodeWithTag(ModesTags.DIALOG_CONFIRM).assertIsNotEnabled()

        composeRule.onNodeWithTag(ModesTags.DIALOG_CANCEL).performClick()
        composeRule.onNodeWithTag(ModesTags.DIALOG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(ModesTags.switch(Mode.PAREJA)).assertIsOff()
        composeRule.onNodeWithTag(ModesTags.CONTINUE).assertIsNotEnabled()
    }

    @Test
    fun b3IsARealModalDialogWithASingleCheckboxNodeToggledFromTheLabel() {
        show()
        composeRule.onNodeWithTag(ModesTags.switch(Mode.PAREJA)).performClick()
        // Ventana propia: los servicios de accesibilidad no alcanzan B2 mientras B3 está abierto.
        composeRule.onNode(isDialog()).assertExists()
        composeRule.onNodeWithTag(ModesTags.DIALOG_CHECK)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertIsOff()
        // La frase se lee una sola vez (sin contentDescription duplicada) y tocarla marca la casilla.
        composeRule.onAllNodesWithText(PAREJA_CONFIRM_TEXT).assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription(
            PAREJA_CONFIRM_TEXT,
            useUnmergedTree = true
        ).assertCountEquals(0)
        composeRule.onNodeWithText(PAREJA_CONFIRM_TEXT).performClick()
        composeRule.onNodeWithTag(ModesTags.DIALOG_CHECK).assertIsOn()
        composeRule.onNodeWithTag(ModesTags.DIALOG_CONFIRM).assertIsEnabled()
    }

    @Test
    fun confirmingB3TurnsParejaOnAndContinueSaves() {
        show()
        composeRule.onNodeWithTag(ModesTags.switch(Mode.PAREJA)).performClick()
        composeRule.onNodeWithTag(ModesTags.DIALOG_CHECK).performClick()
        composeRule.onNodeWithTag(ModesTags.DIALOG_CONFIRM).assertIsEnabled().performClick()
        composeRule.onNodeWithTag(ModesTags.switch(Mode.PAREJA)).assertIsOn()
        composeRule.onNodeWithTag(ModesTags.CONTINUE).performClick()
        composeRule.waitForIdle()
        assertEquals(true, repo.updates.single().second)
    }
}
