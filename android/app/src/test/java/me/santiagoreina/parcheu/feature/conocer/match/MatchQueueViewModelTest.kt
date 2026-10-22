package me.santiagoreina.parcheu.feature.conocer.match

import app.cash.turbine.test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.contract.TicketDto
import me.santiagoreina.parcheu.contract.TicketStatus
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MatchQueueViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeMatchRepository()

    private fun viewModel(mode: Mode? = Mode.AMISTAD) = MatchQueueViewModel(mode, repository)

    @Test
    fun entersQueueWithRouteModeAndShowsSearching() {
        val vm = viewModel(Mode.ESTUDIO)

        assertEquals(listOf(Mode.ESTUDIO), repository.enqueued)
        assertEquals(QueuePhase.Searching, vm.state.value.phase)
        assertEquals(Mode.ESTUDIO, vm.state.value.mode)
    }

    @Test
    fun withoutRouteModeDefaultsToAmistad() {
        assertEquals(Mode.AMISTAD, viewModel(null).state.value.mode)
    }

    @Test
    fun waitingThenMatchedOpensF2() = runTest {
        val vm = viewModel()
        vm.effects.test {
            repository.tickets.emit(waitingTicket())
            assertEquals(QueuePhase.Searching, vm.state.value.phase)

            repository.tickets.emit(matchedTicket("m42"))

            assertEquals(MatchQueueEffect.OpenMatch("m42"), awaitItem())
            assertEquals(QueuePhase.Matched("m42"), vm.state.value.phase)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun matchedOnEnqueueResponseOpensF2Once() = runTest {
        repository.enqueueResult = ApiResult.Success(matchedTicket("m7"))
        val vm = viewModel()
        vm.effects.test {
            assertEquals(MatchQueueEffect.OpenMatch("m7"), awaitItem())
            // El listener repite el mismo ticket: no debe abrir F2 otra vez.
            repository.tickets.emit(matchedTicket("m7"))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun cancelCallsDeleteAndCloses() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onCancel()

            assertEquals(1, repository.cancelCalls)
            assertEquals(MatchQueueEffect.Close, awaitItem())
        }
    }

    @Test
    fun cancelFailureShowsError() {
        repository.cancelResult = ApiResult.Failure(AppError.Network)
        val vm = viewModel()

        vm.onCancel()

        assertEquals(QueuePhase.Error(AppError.Network), vm.state.value.phase)
    }

    @Test
    fun quotaExceededForRandomMatchOpensLimitSheet() {
        val resets = Instant.parse("2026-11-05T05:00:00Z")
        repository.enqueueResult =
            ApiResult.Failure(AppError.QuotaExceeded(QuotaKind.RANDOM_MATCH, resets))

        val vm = viewModel()

        assertEquals(MatchLimitSheet(resets), vm.state.value.limitSheet)
    }

    @Test
    fun quotaExceededForOtherKindDoesNotOpenMatchLimitSheet() {
        repository.enqueueResult =
            ApiResult.Failure(AppError.QuotaExceeded(QuotaKind.SIMULATION, null))

        val vm = viewModel()

        assertNull(vm.state.value.limitSheet)
        assertTrue(vm.state.value.phase is QueuePhase.Error)
    }

    @Test
    fun limitSheetActionsEmitEffects() = runTest {
        repository.enqueueResult =
            ApiResult.Failure(AppError.QuotaExceeded(QuotaKind.RANDOM_MATCH, null))
        val vm = viewModel()
        vm.effects.test {
            vm.onLimitSeePlus()
            assertEquals(MatchQueueEffect.OpenPaywall, awaitItem())
            assertNull(vm.state.value.limitSheet)
            vm.onLimitDismissed()
            assertEquals(MatchQueueEffect.Close, awaitItem())
        }
    }

    @Test
    fun changingModeCancelsAndReEnqueues() {
        val vm = viewModel(Mode.AMISTAD)

        vm.onModeSelected(Mode.PAREJA)

        assertEquals(1, repository.cancelCalls)
        assertEquals(listOf(Mode.AMISTAD, Mode.PAREJA), repository.enqueued)
        assertEquals(Mode.PAREJA, vm.state.value.mode)
    }

    @Test
    fun expiredTicketShowsExpiredAndRetryEnqueuesAgain() {
        val vm = viewModel()
        repository.tickets.tryEmit(TicketDto(TicketStatus.EXPIRED, Mode.AMISTAD, TEST_EXPIRES))
        assertEquals(QueuePhase.Expired, vm.state.value.phase)

        repository.tickets.resetReplayCache()
        vm.onRetry()

        assertEquals(2, repository.enqueued.size)
        assertEquals(QueuePhase.Searching, vm.state.value.phase)
    }

    @Test
    fun ageRequiredShowsError() {
        repository.enqueueResult = ApiResult.Failure(AppError.AgeRequired)

        assertEquals(
            QueuePhase.Error(AppError.AgeRequired),
            viewModel(Mode.PAREJA).state.value.phase
        )
    }
}
