package me.santiagoreina.parcheu.feature.safety

import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SafetySheetViewModelTest {
    @get:Rule
    val dispatcher = MainDispatcherRule()

    private val repo = FakeSafetyRepository()

    @Test
    fun muteChangesMutedAndClosesWithoutBlocking() = runTest {
        val vm = SafetySheetViewModel(FakeSafetyRepository.laura, repo)
        vm.toggleMute()
        assertEquals(listOf("chat-1" to true), repo.mutes)
        assertTrue(repo.blocks.isEmpty())
        assertTrue(vm.state.value.muted)
        assertEquals(SheetStep.Closed, vm.state.value.step)
        assertEquals(CloseReason.Muted, vm.state.value.closedBy)
    }

    @Test
    fun muteFailureKeepsTheSheetOpenWithTheError() = runTest {
        repo.muteResult = FakeSafetyRepository.failure
        val vm = SafetySheetViewModel(FakeSafetyRepository.laura.copy(muted = true), repo)
        vm.toggleMute()
        assertEquals(listOf("chat-1" to false), repo.mutes)
        assertTrue(vm.state.value.muted)
        assertEquals(SheetStep.Menu, vm.state.value.step)
        assertEquals(AppError.Network, vm.state.value.error)
    }

    @Test
    fun blockAsksForConfirmationThenBlocks() = runTest {
        val vm = SafetySheetViewModel(FakeSafetyRepository.laura, repo)
        vm.requestBlock()
        assertEquals(SheetStep.ConfirmBlock, vm.state.value.step)
        assertTrue(repo.blocks.isEmpty())
        vm.confirmBlock()
        assertEquals(listOf(FakeSafetyRepository.laura), repo.blocks)
        assertEquals(SheetStep.Blocked, vm.state.value.step)
        vm.dismiss()
        assertEquals(CloseReason.Blocked, vm.state.value.closedBy)
    }

    @Test
    fun cancelBlockReturnsToTheMenu() = runTest {
        val vm = SafetySheetViewModel(FakeSafetyRepository.laura, repo)
        vm.requestBlock()
        vm.cancelBlock()
        assertEquals(SheetStep.Menu, vm.state.value.step)
        assertTrue(repo.blocks.isEmpty())
    }

    @Test
    fun blockFailureStaysOnConfirmationWithTheError() = runTest {
        repo.blockResult = FakeSafetyRepository.failure
        val vm = SafetySheetViewModel(FakeSafetyRepository.laura, repo)
        vm.requestBlock()
        vm.confirmBlock()
        assertEquals(SheetStep.ConfirmBlock, vm.state.value.step)
        assertEquals(AppError.Network, vm.state.value.error)
    }

    @Test
    fun reportClosesTheSheetForTheHostToOpenH2() = runTest {
        val vm = SafetySheetViewModel(FakeSafetyRepository.laura, repo)
        vm.report()
        assertEquals(CloseReason.Report, vm.state.value.closedBy)
        assertTrue(repo.reports.isEmpty())
    }
}
