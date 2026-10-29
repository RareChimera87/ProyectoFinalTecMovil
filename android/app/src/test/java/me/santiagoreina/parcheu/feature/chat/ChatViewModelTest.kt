package me.santiagoreina.parcheu.feature.chat

import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.ChatStatus
import me.santiagoreina.parcheu.contract.ChatSummary
import me.santiagoreina.parcheu.contract.ChatType
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.feature.safety.SafetyTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val summary =
        ChatSummary(
            id = "chat-1",
            type = ChatType.DIRECT,
            mode = Mode.AMISTAD,
            other = PersonRef(id = "user-2", label = "Laura Pinzón", initials = "LP"),
            muted = false
        )

    private fun remote(
        id: String,
        sender: String?,
        text: String,
        hidden: Boolean = false,
        sec: Long = 1
    ) = RemoteMessage(
        id,
        sender,
        system = sender == null,
        text = text,
        createdAt = Instant.fromEpochSeconds(sec),
        hidden = hidden
    )

    private fun viewModel(repo: FakeChatRepository): ChatViewModel {
        var n = 0
        return ChatViewModel("chat-1", repo) { "client-${++n}" }
    }

    private fun repo() = FakeChatRepository(chats = ApiResult.Success(listOf(summary)))

    @Test
    fun loadsHeaderMarksReadAndMapsOwnMessages() = runTest {
        val repo = repo()
        repo.messages.value =
            listOf(
                remote("m1", "uid-other", "Hola"),
                remote("m2", FakeChatRepository.ME, "Qué más")
            )
        val vm = viewModel(repo)

        val state = vm.state.value
        assertEquals("Laura Pinzón", state.header?.name)
        assertEquals(listOf(false, true), state.messages.map { it.mine })
        assertEquals(1, repo.readCalls)
        assertFalse(state.loading)
    }

    @Test
    fun sentMessageShowsAsPendingThenSent() = runTest {
        val repo = repo()
        val vm = viewModel(repo)
        vm.onDraftChange("  Hola Laura  ")

        vm.send()

        val pending = vm.state.value.messages.single()
        assertEquals(MessageStatus.Pending, pending.status)
        assertEquals("Hola Laura", pending.text)
        assertTrue(pending.mine)
        assertEquals("", vm.state.value.draft)
        assertEquals(Triple("chat-1", "client-1", "Hola Laura"), repo.sent.single())

        repo.completeSend(ApiResult.Success(FakeChatRepository.dto("m9", "Hola Laura")))

        assertEquals(MessageStatus.Sent, vm.state.value.messages.single().status)
    }

    @Test
    fun snapshotReplacesTheLocalCopyWithoutDuplicating() = runTest {
        val repo = repo()
        val vm = viewModel(repo)
        vm.onDraftChange("Hola")
        vm.send()
        repo.completeSend(ApiResult.Success(FakeChatRepository.dto("m9", "Hola")))

        repo.messages.value = listOf(remote("m9", FakeChatRepository.ME, "Hola"))

        val messages = vm.state.value.messages
        assertEquals(1, messages.size)
        assertEquals("m9", messages.single().key)
    }

    @Test
    fun failedSendShowsRetryAndRetryReusesClientId() = runTest {
        val repo = repo()
        val vm = viewModel(repo)
        vm.onDraftChange("Hola")
        vm.send()

        repo.completeSend(FakeChatRepository.failure)

        val failed = vm.state.value.messages.single()
        assertEquals(MessageStatus.Failed(AppError.Network), failed.status)

        vm.retry(failed.key)
        assertEquals(MessageStatus.Pending, vm.state.value.messages.single().status)
        assertEquals(listOf("client-1", "client-1"), repo.sent.map { it.second })

        repo.completeSend(ApiResult.Success(FakeChatRepository.dto("m9", "Hola")))
        assertEquals(MessageStatus.Sent, vm.state.value.messages.single().status)
    }

    @Test
    fun hiddenRemoteMessageKeepsFlagAndDisplaysPlaceholder() = runTest {
        val repo = repo()
        repo.messages.value = listOf(remote("m1", "uid-other", "texto ofensivo", hidden = true))
        val vm = viewModel(repo)

        val message = vm.state.value.messages.single()
        assertTrue(message.hidden)
        assertEquals("Mensaje oculto", message.displayText)
    }

    @Test
    fun blankDraftIsNotSentAndDraftIsCapped() = runTest {
        val repo = repo()
        val vm = viewModel(repo)
        vm.onDraftChange("   ")
        vm.send()
        assertTrue(repo.sent.isEmpty())

        vm.onDraftChange("a".repeat(MAX_MESSAGE_LENGTH + 50))
        assertEquals(MAX_MESSAGE_LENGTH, vm.state.value.draft.length)
    }

    @Test
    fun muteTogglesAndRollsBackOnFailure() = runTest {
        val repo = repo()
        val vm = viewModel(repo)
        vm.toggleMute()
        assertTrue(vm.state.value.header!!.muted)

        repo.muteResult = FakeChatRepository.failure
        vm.toggleMute()
        assertTrue(vm.state.value.header!!.muted)
        assertEquals(ChatEffect.MuteFailed(AppError.Network), vm.effects.first())
    }

    @Test
    fun reportOpensH1WithTheOtherPerson() = runTest {
        val vm = viewModel(repo())
        vm.report()
        assertEquals(
            ChatEffect.OpenReport(
                SafetyTarget(
                    "user-2",
                    "Laura Pinzón",
                    anonymous = false,
                    chatId = "chat-1",
                    muted = false
                )
            ),
            vm.effects.first()
        )
    }

    @Test
    fun closedChatCannotSend() = runTest {
        val repo =
            FakeChatRepository(
                chats = ApiResult.Success(listOf(summary.copy(status = ChatStatus.CLOSED)))
            )
        val vm = viewModel(repo)
        vm.onDraftChange("Hola")
        assertFalse(vm.state.value.canSend)
        vm.send()
        assertTrue(repo.sent.isEmpty())
    }

    @Test
    fun headerFailureIsReportedAndRetryRecovers() = runTest {
        val repo = FakeChatRepository(chats = FakeChatRepository.failure)
        val vm = viewModel(repo)
        assertEquals(AppError.Network, vm.state.value.headerError)
        assertNull(vm.state.value.header)

        repo.chats = ApiResult.Success(listOf(summary))
        vm.retryLoad()
        assertNull(vm.state.value.headerError)
        assertEquals("Laura Pinzón", vm.state.value.header?.name)
    }

    @Test
    fun liveListenerFailureShowsErrorAndRetryResubscribes() = runTest {
        val repo = repo()
        repo.liveFailure =
            ChatLiveException(AppError.Forbidden)
        val vm = viewModel(repo)
        assertEquals(AppError.Forbidden, vm.state.value.liveError)
        assertFalse(vm.state.value.loading)

        repo.liveFailure = null
        repo.messages.value = listOf(remote("m1", "other", "hola"))
        vm.retryLive()
        assertNull(vm.state.value.liveError)
        assertEquals(2, repo.observeCalls)
        assertEquals(1, vm.state.value.messages.size)
    }

    @Test
    fun networkListenerFailureMapsToNetwork() = runTest {
        val repo = repo()
        repo.liveFailure = java.io.IOException("sin red")
        val vm = viewModel(repo)
        assertEquals(AppError.Network, vm.state.value.liveError)
    }

    @Test
    fun anonymousChatReportsWithAnonIdNeverAsUserId() = runTest {
        val anon =
            summary.copy(
                type = ChatType.SCENARIO,
                other = PersonRef(anonId = "hmac-anon", label = "Alguien de la UIS", initials = "A")
            )
        val repo = FakeChatRepository(chats = ApiResult.Success(listOf(anon)))
        val vm = viewModel(repo)
        assertNull(vm.state.value.header?.otherUserId)
        assertTrue(vm.state.value.header!!.canReport)
        vm.report()
        // El anonId viaja marcado como anónimo: H2 lo manda como `anonId` y el servidor lo resuelve.
        val target = (vm.effects.first() as ChatEffect.OpenReport).target
        assertEquals("hmac-anon", target.id)
        assertTrue(target.anonymous)
        assertEquals("chat-1", target.chatId)
    }

    private fun history(count: Int) = (1..count).map {
        remote("h$it", "uid-2", "mensaje $it", sec = it.toLong())
    }

    @Test
    fun shortLiveWindowHasNothingOlderToLoad() = runTest {
        val repo = repo()
        val vm = viewModel(repo)
        repo.messages.value = history(CHAT_PAGE_SIZE - 1)

        assertFalse(vm.state.value.canLoadOlder)
        vm.loadOlder()
        assertTrue(repo.loadOlderCalls.isEmpty())
    }

    @Test
    fun loadOlderPrependsThePreviousPageStartingAtTheOldestKnownMessage() = runTest {
        val repo = repo()
        val all = history(CHAT_PAGE_SIZE * 2 + 10)
        repo.history = all
        val vm = viewModel(repo)
        repo.messages.value = all.takeLast(CHAT_PAGE_SIZE)
        assertTrue(vm.state.value.canLoadOlder)

        vm.loadOlder()

        assertEquals(listOf(61L), repo.loadOlderCalls.map { it.seconds })
        val keys = vm.state.value.messages.map { it.key }
        assertEquals(CHAT_PAGE_SIZE * 2, keys.size)
        assertEquals("h11", keys.first())
        assertEquals("h110", keys.last())
        assertTrue(vm.state.value.canLoadOlder)
        assertFalse(vm.state.value.loadingOlder)

        vm.loadOlder()

        assertEquals(listOf(61L, 11L), repo.loadOlderCalls.map { it.seconds })
        // La última página trae solo 10 mensajes: el historial se da por agotado.
        assertEquals(CHAT_PAGE_SIZE * 2 + 10, vm.state.value.messages.size)
        assertFalse(vm.state.value.canLoadOlder)
        vm.loadOlder()
        assertEquals(2, repo.loadOlderCalls.size)
    }

    @Test
    fun loadOlderFailureKeepsMessagesAndAllowsRetry() = runTest {
        val repo = repo()
        val all = history(CHAT_PAGE_SIZE + 5)
        repo.history = all
        val vm = viewModel(repo)
        repo.messages.value = all.takeLast(CHAT_PAGE_SIZE)
        repo.loadOlderFailure = ChatLiveException(AppError.Network)

        vm.loadOlder()

        assertEquals(CHAT_PAGE_SIZE, vm.state.value.messages.size)
        assertFalse(vm.state.value.loadingOlder)
        assertTrue(vm.state.value.canLoadOlder)

        repo.loadOlderFailure = null
        vm.loadOlder()
        assertEquals(CHAT_PAGE_SIZE + 5, vm.state.value.messages.size)
    }

    @Test
    fun messagesPushedOutOfTheLiveWindowStayInTheList() = runTest {
        val repo = repo()
        val all = history(CHAT_PAGE_SIZE + 1)
        val vm = viewModel(repo)
        repo.messages.value = all.take(CHAT_PAGE_SIZE)

        repo.messages.value = all.takeLast(CHAT_PAGE_SIZE)

        val keys = vm.state.value.messages.map { it.key }
        assertEquals(CHAT_PAGE_SIZE + 1, keys.size)
        assertEquals("h1", keys.first())
        assertEquals("h${CHAT_PAGE_SIZE + 1}", keys.last())
    }
}
