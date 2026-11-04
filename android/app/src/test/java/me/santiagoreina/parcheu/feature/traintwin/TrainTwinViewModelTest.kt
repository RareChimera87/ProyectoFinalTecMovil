package me.santiagoreina.parcheu.feature.traintwin

import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.MemoryStatus
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrainTwinViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun content(vm: TrainTwinViewModel) =
        (vm.state.value as TrainTwinUiState.Loaded).content

    private fun started(repo: FakeTrainTwinRepository = FakeTrainTwinRepository()) =
        TrainTwinViewModel(repo).also { it.start() }

    @Test
    fun loadsMemoriesAndPrompts() = runTest {
        val vm = started()
        val c = content(vm)
        assertEquals(listOf(TrainTwinSamples.pending), c.pending)
        assertEquals(listOf(TrainTwinSamples.accepted), c.accepted)
        assertEquals(TrainTwinSamples.control, c.control)
        assertEquals(listOf(TrainTwinSamples.rating), c.ratings)
    }

    @Test
    fun anyLoadFailureShowsErrorAndRetryRecovers() = runTest {
        val repo = FakeTrainTwinRepository(prompts = FakeTrainTwinRepository.failure)
        val vm = started(repo)
        assertEquals(TrainTwinUiState.Error(AppError.Network), vm.state.value)
        repo.prompts = ApiResult.Success(emptyList())
        vm.retry()
        assertTrue(vm.state.value is TrainTwinUiState.Loaded)
    }

    @Test
    fun acceptingMovesMemoryFromPendingToAccepted() = runTest {
        val repo = FakeTrainTwinRepository()
        val vm = started(repo)
        vm.onAccept(TrainTwinSamples.pending)
        val c = content(vm)
        assertEquals(emptyList<Any>(), c.pending)
        assertEquals(listOf("m-1", "m-2"), c.accepted.map { it.id })
        assertEquals(MemoryStatus.ACCEPTED, c.accepted.first().status)
        assertEquals(listOf("accept:m-1"), repo.calls)
    }

    @Test
    fun rejectingRemovesMemoryAndFailureKeepsItWithAMessage() = runTest {
        val repo = FakeTrainTwinRepository()
        val vm = started(repo)
        repo.decision = FakeTrainTwinRepository.failure
        vm.onReject(TrainTwinSamples.pending)
        assertEquals(1, content(vm).pending.size)
        assertNotNull(content(vm).message)
        assertTrue(content(vm).busy.isEmpty())

        repo.decision = null
        vm.onReject(TrainTwinSamples.pending)
        assertEquals(emptyList<Any>(), content(vm).pending)
        assertEquals(1, content(vm).accepted.size)
        assertNull(content(vm).message)
    }

    @Test
    fun ratingRemovesThePromptAndSendsTheRating() = runTest {
        val repo = FakeTrainTwinRepository()
        val vm = started(repo)
        vm.onRate("sim-1:2", positive = true)
        assertEquals(emptyList<Any>(), content(vm).ratings)
        assertEquals(listOf("rate:sim-1:2:true"), repo.calls)
    }

    @Test
    fun controlAnswerSendsTheCodeOfTheChosenOption() = runTest {
        val repo = FakeTrainTwinRepository()
        val vm = started(repo)
        vm.onControlAnswer("c-1", 2)
        assertNull(content(vm).control)
        assertEquals(listOf("control:c-1:DEPENDS"), repo.calls)
        vm.onControlAnswer("c-1", 7)
        assertEquals(1, repo.calls.size)
    }

    @Test
    fun failedSendKeepsThePromptForRetry() = runTest {
        val repo = FakeTrainTwinRepository(sendResult = FakeTrainTwinRepository.failure)
        val vm = started(repo)
        vm.onRate("sim-1:2", positive = false)
        assertEquals(1, content(vm).ratings.size)
        assertNotNull(content(vm).message)
        vm.onDismissMessage()
        assertNull(content(vm).message)
    }

    @Test
    fun seeAllShowsEveryMemoryAndOnlyOffersItWhenThereIsMore() = runTest {
        val many = (1..5).map { TrainTwinSamples.pending.copy(id = "p$it") }
        val vm = started(FakeTrainTwinRepository(pending = ApiResult.Success(many)))
        assertTrue(content(vm).canExpand)
        assertEquals(MEMORIES_PREVIEW, content(vm).visiblePending.size)
        vm.onShowAll()
        assertEquals(5, content(vm).visiblePending.size)
        assertFalse(started().let { content(it).canExpand })
    }

    @Test
    fun turnRefParsesSimulationAndTurnIndex() {
        assertEquals(TurnRef("sim-1", 2), parseTurnRef("sim-1:2"))
        assertNull(parseTurnRef("c-1"))
        assertNull(parseTurnRef("sim-1:x"))
        assertNull(parseTurnRef(":3"))
    }
}
