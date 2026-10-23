package me.santiagoreina.parcheu.feature.chatlist

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.ChatSummary
import me.santiagoreina.parcheu.contract.ChatType
import me.santiagoreina.parcheu.contract.MessageDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.ui.UiState
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.feature.chat.FakeChatRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatListViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun chat(id: String, at: Long?) = ChatSummary(
        id = id,
        type = ChatType.DIRECT,
        mode = Mode.ESTUDIO,
        other = PersonRef(id = "u-$id", label = "Persona $id", initials = "P"),
        lastMessage = at?.let {
            MessageDto("m-$id", null, "hola", Instant.fromEpochSeconds(it))
        }
    )

    @Test
    fun emptyListIsEmptyState() {
        val vm = ChatListViewModel(FakeChatRepository(chats = ApiResult.Success(emptyList())))
        assertEquals(UiState.Empty, vm.state.value)
    }

    @Test
    fun mostRecentChatFirstAndChatsWithoutMessagesLast() {
        val vm =
            ChatListViewModel(
                FakeChatRepository(
                    chats = ApiResult.Success(listOf(chat("a", null), chat("b", 10), chat("c", 20)))
                )
            )
        val loaded = vm.state.value as UiState.Loaded
        assertEquals(listOf("c", "b", "a"), loaded.data.map { it.id })
    }

    @Test
    fun failureShowsErrorAndRetryRecovers() {
        val repo = FakeChatRepository(chats = FakeChatRepository.failure)
        val vm = ChatListViewModel(repo)
        assertTrue(vm.state.value is UiState.Error)

        repo.chats = ApiResult.Success(listOf(chat("a", 1)))
        vm.retry()
        assertTrue(vm.state.value is UiState.Loaded)
    }

    @Test
    fun refreshFailureKeepsTheLoadedList() {
        val repo = FakeChatRepository(chats = ApiResult.Success(listOf(chat("a", 1))))
        val vm = ChatListViewModel(repo)
        repo.chats = FakeChatRepository.failure
        vm.refresh()
        assertTrue(vm.state.value is UiState.Loaded)
    }
}
