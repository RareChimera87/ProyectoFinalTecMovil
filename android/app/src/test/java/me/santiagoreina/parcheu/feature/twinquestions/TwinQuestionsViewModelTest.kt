package me.santiagoreina.parcheu.feature.twinquestions

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.santiagoreina.parcheu.contract.Answer
import me.santiagoreina.parcheu.contract.TwinAnswer
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TwinQuestionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeTwinQuestionsRepository()
    private val store = InMemoryAnswerProgressStore()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TwinQuestionsViewModel(repository, store)

    @Test
    fun swipeLeftIsAAndSwipeRightIsBAndDependsIsDepends() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(TwinQuestionsPhase.Answering, vm.state.value.phase)
        assertEquals(1, vm.state.value.currentNumber)

        vm.answer(TwinAnswer.A)
        vm.answer(TwinAnswer.B)
        vm.answer(TwinAnswer.DEPENDS)
        advanceUntilIdle()

        assertEquals(
            mapOf(1 to TwinAnswer.A, 2 to TwinAnswer.B, 3 to TwinAnswer.DEPENDS),
            vm.state.value.answers
        )
        assertEquals(4, vm.state.value.currentNumber)
        assertEquals("Pregunta 4", vm.state.value.current?.text)
    }

    @Test
    fun answeringTheTwelfthSubmitsExactlyOnce() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.events.test {
            repeat(11) { vm.answer(TwinAnswer.A) }
            advanceUntilIdle()
            assertTrue(repository.submitted.isEmpty())

            vm.answer(TwinAnswer.B)
            // Respuestas extra mientras se envía no duplican el envío.
            vm.answer(TwinAnswer.A)
            vm.answer(TwinAnswer.DEPENDS)
            advanceUntilIdle()

            assertEquals(1, repository.submitted.size)
            val items = repository.submitted.single().items
            assertEquals(12, items.size)
            assertEquals(Answer(12, TwinAnswer.B), items.last())
            assertEquals(TwinQuestionsEvent.Finished, awaitItem())
            assertEquals(TwinQuestionsPhase.Done, vm.state.value.phase)
            assertTrue(store.data.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reopeningResumesFromTheLastSavedAnswer() = runTest(dispatcher) {
        val first = viewModel()
        advanceUntilIdle()
        first.answer(TwinAnswer.A)
        first.answer(TwinAnswer.DEPENDS)
        first.answer(TwinAnswer.B)
        advanceUntilIdle()

        val reopened = viewModel()
        advanceUntilIdle()

        assertEquals(3, reopened.state.value.answeredCount)
        assertEquals(4, reopened.state.value.currentNumber)
        assertEquals(TwinAnswer.DEPENDS, reopened.state.value.answers[2])
        assertTrue(repository.submitted.isEmpty())
    }

    @Test
    fun savedAnswersForUnknownQuestionsAreDiscarded() = runTest(dispatcher) {
        store.data = mapOf(1 to TwinAnswer.A, 99 to TwinAnswer.B)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(mapOf(1 to TwinAnswer.A), vm.state.value.answers)
    }

    @Test
    fun submitFailureShowsErrorKeepsProgressAndRetrySubmitsAgain() = runTest(dispatcher) {
        repository.submitResults = mutableListOf(networkFailure)
        val vm = viewModel()
        advanceUntilIdle()
        repeat(12) { vm.answer(TwinAnswer.A) }
        advanceUntilIdle()

        assertEquals(TwinQuestionsPhase.Error, vm.state.value.phase)
        assertEquals(AppError.Network, vm.state.value.error)
        assertEquals(12, store.data.size)

        vm.events.test {
            vm.retry()
            advanceUntilIdle()
            assertEquals(2, repository.submitted.size)
            assertEquals(TwinQuestionsEvent.Finished, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(vm.state.value.error)
    }

    @Test
    fun retryAfterLoadFailureReloadsQuestions() = runTest(dispatcher) {
        repository.questions = ApiResult.Failure(AppError.Network)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(TwinQuestionsPhase.Error, vm.state.value.phase)

        repository.questions = ApiResult.Success(sampleQuestions())
        vm.retry()
        advanceUntilIdle()
        assertEquals(TwinQuestionsPhase.Answering, vm.state.value.phase)
        assertEquals(12, vm.state.value.total)
    }

    @Test
    fun fullySavedProgressSubmitsOnReopen() = runTest(dispatcher) {
        store.data = (1..12).associateWith { TwinAnswer.A }
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(1, repository.submitted.size)
        assertEquals(TwinQuestionsPhase.Done, vm.state.value.phase)
    }
}
