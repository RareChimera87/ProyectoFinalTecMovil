package me.santiagoreina.parcheu.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.ChatSummary
import me.santiagoreina.parcheu.contract.ChatType
import me.santiagoreina.parcheu.contract.MessageDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.ui.UiState
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.chat.ChatActions
import me.santiagoreina.parcheu.feature.chat.ChatHeader
import me.santiagoreina.parcheu.feature.chat.ChatInputBar
import me.santiagoreina.parcheu.feature.chat.ChatMessageUi
import me.santiagoreina.parcheu.feature.chat.ChatScreen
import me.santiagoreina.parcheu.feature.chat.ChatUiState
import me.santiagoreina.parcheu.feature.chat.MessageStatus
import me.santiagoreina.parcheu.feature.chatlist.ChatListScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de F9 y de la lista de chats (con chats y vacía) en ambos temas, contra las PNG de
 * app/src/test/screenshots/chat. Grabar con `./gradlew :app:recordRoborazziDevDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class ChatScreenshotTest(private val dark: Boolean) {
    @get:Rule
    val composeRule = createComposeRule()

    private val theme = if (dark) "dark" else "light"

    private fun capture(name: String, content: @Composable () -> Unit) {
        // La hora de las burbujas depende de la zona horaria: se fija para que la captura sea reproducible.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        composeRule.setContent { ParcheUTheme(darkTheme = dark) { content() } }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/chat/${name}_$theme.png",
            roborazziOptions =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    private val header =
        ChatHeader(
            "c1",
            "Laura Pinzón",
            "LP",
            false,
            "u2",
            Mode.ESTUDIO,
            muted = false,
            closed = false
        )

    private val at = Instant.fromEpochSeconds(1_761_130_000)

    private fun chat(
        id: String,
        name: String,
        initials: String,
        text: String,
        unread: Int,
        hidden: Boolean = false,
        mode: Mode = Mode.AMISTAD,
        muted: Boolean = false
    ) = ChatSummary(
        id = id,
        type = ChatType.DIRECT,
        mode = mode,
        muted = muted,
        other = PersonRef(id = "u-$id", label = name, initials = initials),
        lastMessage = MessageDto("m-$id", null, text, at, hidden),
        unread = unread
    )

    @Test
    fun f9Chat() = capture("f9_chat") {
        ChatScreen(
            ChatUiState(
                loading = false,
                header = header,
                messages =
                listOf(
                    ChatMessageUi(
                        "1",
                        "Hola Laura, vi que también estudias Cálculo II",
                        mine = true,
                        createdAt = at
                    ),
                    ChatMessageUi(
                        "2",
                        "¡Hola! Sí, ¿armamos grupo para el parcial?",
                        mine = false,
                        createdAt = at
                    ),
                    ChatMessageUi(
                        "3",
                        "mensaje moderado",
                        mine = false,
                        hidden = true,
                        createdAt = at
                    ),
                    ChatMessageUi(
                        "4",
                        "Dale, el jueves en la biblioteca",
                        mine = true,
                        createdAt = at
                    ),
                    ChatMessageUi(
                        "5",
                        "No llegó",
                        mine = true,
                        status = MessageStatus.Failed(AppError.Network)
                    ),
                    ChatMessageUi("6", "Enviándose", mine = true, status = MessageStatus.Pending)
                ),
                draft = "Perfecto"
            ),
            ChatActions()
        )
    }

    @Test
    fun f9ChatDemo() = capture("f9_chat_demo") {
        ChatScreen(
            ChatUiState(
                loading = false,
                // El servidor manda "V(" para "Valentina (demo)"; el avatar debe mostrar solo "V".
                header = header.copy(name = "Valentina (demo)", initials = "V(", isDemo = true),
                messages =
                listOf(
                    ChatMessageUi("1", "Hola, ¿qué tal el parcial?", mine = true, createdAt = at)
                )
            ),
            ChatActions()
        )
    }

    @Test
    fun chatListWithChats() = capture("chat_list") {
        ChatListScreen(
            UiState.Loaded(
                listOf(
                    chat(
                        "1",
                        "Laura Pinzón",
                        "LP",
                        "¿Armamos grupo para el parcial?",
                        unread = 2,
                        mode = Mode.ESTUDIO
                    ),
                    chat("2", "Camilo Rojas", "CR", "texto moderado", unread = 0, hidden = true),
                    chat(
                        "3",
                        "Valentina Gómez",
                        "VG",
                        "Nos vemos mañana",
                        unread = 0,
                        mode = Mode.PAREJA,
                        muted = true
                    )
                )
            ),
            onOpenChat = {},
            onRetry = {}
        )
    }

    @Test
    fun chatListEmpty() = capture("chat_list_empty") {
        ChatListScreen(UiState.Empty, onOpenChat = {}, onRetry = {})
    }

    @Test
    fun chatListOffline() = capture("chat_list_sin_conexion") {
        ChatListScreen(UiState.Error(AppError.Network), onOpenChat = {}, onRetry = {})
    }

    /** Barra de envío con texto (botón habilitado, violeta pleno) y vacía (deshabilitado, s2 con borde). */
    @Test
    fun inputEnabled() = captureNode("f9_input_enabled") {
        ChatInputBar("¿Estudiamos mañana?", {
        }, {}, canSend = true, enabled = true, placeholder = "Escribe un mensaje")
    }

    @Test
    fun inputDisabled() = captureNode("f9_input_disabled") {
        ChatInputBar("", {
        }, {}, canSend = false, enabled = true, placeholder = "Escribe un mensaje")
    }

    private fun captureNode(name: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            ParcheUTheme(darkTheme = dark) {
                Box(Modifier.background(ParcheUTheme.colors.bg).testTag("input")) { content() }
            }
        }
        composeRule.onNodeWithTag("input").captureRoboImage(
            filePath = "src/test/screenshots/chat/${name}_$theme.png",
            roborazziOptions =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
    }
}
