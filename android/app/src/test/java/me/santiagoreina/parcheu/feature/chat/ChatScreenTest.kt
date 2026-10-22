package me.santiagoreina.parcheu.feature.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val header =
        ChatHeader(
            "c1",
            "Laura Pinzón",
            "LP",
            false,
            "u2",
            Mode.AMISTAD,
            muted = false,
            closed = false
        )

    private fun show(state: ChatUiState, actions: ChatActions = ChatActions()) {
        composeRule.setContent { ParcheUTheme { ChatScreen(state, actions) } }
    }

    @Test
    fun hiddenMessageRendersAsMensajeOculto() {
        show(
            ChatUiState(
                loading = false,
                header = header,
                messages =
                listOf(
                    ChatMessageUi("m1", "texto que no debe verse", mine = false, hidden = true),
                    ChatMessageUi("m2", "Hola normal", mine = true)
                )
            )
        )
        composeRule.onNodeWithText("Mensaje oculto").assertIsDisplayed()
        composeRule.onNodeWithText("texto que no debe verse").assertDoesNotExist()
        composeRule.onNodeWithText("Hola normal").assertIsDisplayed()
    }

    @Test
    fun failedMessageOffersRetry() {
        var retried: String? = null
        show(
            ChatUiState(
                loading = false,
                header = header,
                messages =
                listOf(
                    ChatMessageUi(
                        "k1",
                        "No llegó",
                        mine = true,
                        status = MessageStatus.Failed(AppError.Network)
                    )
                )
            ),
            ChatActions(onRetryMessage = { retried = it })
        )
        composeRule.onNodeWithTag(ChatTags.RETRY).performClick()
        assertEquals("k1", retried)
    }

    @Test
    fun menuOffersMuteAndReport() {
        var reported = false
        show(
            ChatUiState(loading = false, header = header),
            ChatActions(onReport = {
                reported = true
            })
        )
        composeRule.onNodeWithTag(ChatTags.MENU).performClick()
        composeRule.onNodeWithTag(ChatTags.MENU_MUTE).assertIsDisplayed()
        composeRule.onNodeWithTag(ChatTags.MENU_REPORT).performClick()
        assertEquals(true, reported)
    }

    @Test
    fun consentBannerShowsAndDismisses() {
        var dismissed = false
        show(
            ChatUiState(loading = false, header = header),
            ChatActions(onDismissConsent = { dismissed = true })
        )
        composeRule.onNodeWithTag(ChatTags.CONSENT_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText("Entendido").performClick()
        assertEquals(true, dismissed)
    }
}
