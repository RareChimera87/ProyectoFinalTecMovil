package me.santiagoreina.parcheu.feature.blockedusers

import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.BlockedDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.core.ui.UiState
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.feature.safety.FakeSafetyRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BlockedUsersViewModelTest {
    @get:Rule
    val dispatcher = MainDispatcherRule()

    private val repo = FakeSafetyRepository()

    private val laura =
        BlockedDto("u-laura", "Laura Pérez", "LP", Instant.parse("2026-11-10T15:00:00Z"))
    private val anon =
        BlockedDto("a_x", "Alguien de la UIS", "A", Instant.parse("2026-11-12T15:00:00Z"))

    @Test
    fun emptyListShowsTheEmptyState() = runTest {
        val vm = BlockedUsersViewModel(repo)
        assertEquals(UiState.Empty, vm.state.value.list)
    }

    @Test
    fun listsTheMostRecentBlockFirst() = runTest {
        repo.blocksResult = ApiResult.Success(listOf(laura, anon))
        val vm = BlockedUsersViewModel(repo)
        assertEquals(listOf(anon, laura), (vm.state.value.list as UiState.Loaded).data)
    }

    @Test
    fun unblockCallsTheApiWithThePresentedIdAndRemovesTheRow() = runTest {
        repo.blocksResult = ApiResult.Success(listOf(laura, anon))
        val vm = BlockedUsersViewModel(repo)
        vm.unblock("a_x")
        assertEquals(listOf("a_x"), repo.unblocks)
        assertEquals(listOf(laura), (vm.state.value.list as UiState.Loaded).data)
        assertNull(vm.state.value.unblocking)
        vm.unblock("u-laura")
        assertEquals(UiState.Empty, vm.state.value.list)
    }

    @Test
    fun failedUnblockKeepsTheRowAndShowsTheError() = runTest {
        repo.blocksResult = ApiResult.Success(listOf(laura))
        repo.unblockResult = ApiResult.Failure(AppError.Network)
        val vm = BlockedUsersViewModel(repo)
        vm.unblock("u-laura")
        assertEquals(listOf(laura), (vm.state.value.list as UiState.Loaded).data)
        assertEquals(AppError.Network, vm.state.value.actionError)
        vm.dismissError()
        assertNull(vm.state.value.actionError)
    }

    @Test
    fun loadErrorThenRetryRecovers() = runTest {
        repo.blocksResult = ApiResult.Failure(AppError.Network)
        val vm = BlockedUsersViewModel(repo)
        assertTrue(vm.state.value.list is UiState.Error)
        repo.blocksResult = ApiResult.Success(listOf(laura))
        vm.retry()
        assertEquals(listOf(laura), (vm.state.value.list as UiState.Loaded).data)
    }
}
