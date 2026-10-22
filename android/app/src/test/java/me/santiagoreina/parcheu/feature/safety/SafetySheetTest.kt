package me.santiagoreina.parcheu.feature.safety

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** H1 con su ViewModel real y el repositorio en memoria. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SafetySheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val dispatcher = MainDispatcherRule()

    private val repo = FakeSafetyRepository()

    private fun show(target: SafetyTarget = FakeSafetyRepository.laura): SafetySheetViewModel {
        val vm = SafetySheetViewModel(target, repo)
        composeRule.setContent {
            val state by vm.state.collectAsState()
            ParcheUTheme {
                SafetySheet(
                    state,
                    SafetySheetActions(
                        onMute = vm::toggleMute,
                        onReport = vm::report,
                        onBlock = vm::requestBlock,
                        onConfirmBlock = vm::confirmBlock,
                        onCancelBlock = vm::cancelBlock,
                        onDismiss = vm::dismiss,
                    ),
                )
            }
        }
        return vm
    }

    @Test
    fun silenciarChangesMutedAndClosesTheSheetWithoutBlocking() {
        val vm = show()
        composeRule.onNodeWithText("Silenciar a Laura").assertIsDisplayed()
        composeRule.onNodeWithTag(SafetyTags.MUTE).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("chat-1" to true), repo.mutes)
        assertTrue(repo.blocks.isEmpty())
        assertTrue(vm.state.value.muted)
        assertEquals(CloseReason.Muted, vm.state.value.closedBy)
        composeRule.onNodeWithTag(SafetyTags.MUTE).assertDoesNotExist()
    }

    @Test
    fun blockingNeedsConfirmationAndShowsTheBlockedConfirmation() {
        show()
        composeRule.onNodeWithTag(SafetyTags.BLOCK).performClick()
        composeRule.onNodeWithText("¿Bloquear a Laura?").assertIsDisplayed()
        assertTrue(repo.blocks.isEmpty())
        composeRule.onNodeWithTag(SafetyTags.CONFIRM_BLOCK).performClick()
        composeRule.onNodeWithText("Bloqueaste a Laura").assertIsDisplayed()
        assertEquals(1, repo.blocks.size)
    }

    @Test
    fun withoutChatThereIsNoSilenciar() {
        show(FakeSafetyRepository.laura.copy(chatId = null))
        composeRule.onNodeWithTag(SafetyTags.REPORT).assertIsDisplayed()
        composeRule.onNodeWithTag(SafetyTags.MUTE).assertDoesNotExist()
    }

    @Test
    fun anonymousPersonIsNeverNamed() {
        show(SafetyTarget("a_x", "Alguien de la UIS", anonymous = true, chatId = "c2"))
        composeRule.onNodeWithText("Reportar a esta persona").assertIsDisplayed()
        composeRule.onNodeWithText("Bloquear a esta persona").assertIsDisplayed()
    }
}
