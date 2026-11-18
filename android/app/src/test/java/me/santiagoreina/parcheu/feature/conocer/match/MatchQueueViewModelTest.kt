package me.santiagoreina.parcheu.feature.conocer.match

import app.cash.turbine.test
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.contract.MatchStatus
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.contract.TicketDto
import me.santiagoreina.parcheu.contract.TicketStatus
import me.santiagoreina.parcheu.contract.Usage
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.feature.plus.PlusSources
import me.santiagoreina.parcheu.navigation.Paywall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private var clock = 1_000_000L

    private fun clockedVm(mode: Mode? = null) =
        MatchQueueViewModel(mode, repository, now = { clock })

    @Test
    fun cancelSearchLeavesIdleWithoutClosingAndRemembersIt() = runTest {
        val vm = clockedVm(Mode.PAREJA)
        vm.effects.test {
            vm.onCancelSearch()

            assertEquals(1, repository.cancelCalls)
            assertEquals(QueuePhase.Idle, vm.state.value.phase)
            assertEquals(Mode.PAREJA, vm.state.value.mode)
            assertEquals(clock, repository.searchCancelledAt)
            expectNoEvents()
            // Volver a primer plano no la reactiva.
            vm.onResumed()
            assertEquals(1, repository.enqueued.size)
            assertEquals(QueuePhase.Idle, vm.state.value.phase)
        }
    }

    @Test
    fun cancelSearchFailureShowsErrorAndKeepsNoMark() {
        repository.cancelResult = ApiResult.Failure(AppError.Network)
        val vm = clockedVm(Mode.PAREJA)

        vm.onCancelSearch()

        assertEquals(QueuePhase.Error(AppError.Network), vm.state.value.phase)
        assertNull(repository.searchCancelledAt)
    }

    @Test
    fun aNewScreenAfterACancelDoesNotEnqueueByItself() {
        repository.lastMode = Mode.PAREJA
        repository.searchCancelledAt = clock - 60_000

        val vm = clockedVm()

        assertEquals(QueuePhase.Idle, vm.state.value.phase)
        assertEquals(Mode.PAREJA, vm.state.value.mode)
        assertTrue(repository.enqueued.isEmpty())
        assertEquals(0, repository.currentTicketCalls)
    }

    @Test
    fun searchFromIdleAfterACancelClearsTheMark() {
        repository.searchCancelledAt = clock - 1
        val vm = clockedVm()

        vm.onSearch()

        assertEquals(QueuePhase.Searching, vm.state.value.phase)
        assertEquals(1, repository.enqueued.size)
        assertNull(repository.searchCancelledAt)
    }

    @Test
    fun anOldCancelMarkIsIgnored() {
        repository.searchCancelledAt = clock - MatchQueueViewModel.CANCEL_MEMORY_MS - 1

        val vm = clockedVm()

        assertEquals(QueuePhase.Searching, vm.state.value.phase)
        assertEquals(1, repository.enqueued.size)
    }

    @Test
    fun anExplicitModeSearchesEvenWithARecentCancel() {
        repository.searchCancelledAt = clock - 1

        val vm = clockedVm(Mode.ESTUDIO)

        assertEquals(QueuePhase.Searching, vm.state.value.phase)
        assertEquals(listOf(Mode.ESTUDIO), repository.enqueued)
    }

    @Test
    fun theXAfterSearchingAlsoRemembersTheCancel() = runTest {
        val vm = clockedVm(Mode.AMISTAD)
        vm.effects.test {
            vm.onCancel()
            assertEquals(MatchQueueEffect.Close, awaitItem())
            assertEquals(clock, repository.searchCancelledAt)
        }
    }

    @Test
    fun resumingAfterTheXReentersTheQueue() = runTest {
        // Red de seguridad de A1: si la F1 cancelada se restaurara igual, no se queda "Buscando…" sin fila.
        val vm = viewModel()
        vm.effects.test {
            vm.onCancel()
            assertEquals(MatchQueueEffect.Close, awaitItem())
            assertEquals(1, repository.enqueued.size)

            vm.onResumed()

            assertEquals(2, repository.enqueued.size)
            assertEquals(QueuePhase.Searching, vm.state.value.phase)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun resumingWithALiveSearchDoesNothing() {
        val vm = viewModel()
        val ticketCalls = repository.currentTicketCalls

        vm.onResumed()

        assertEquals(1, repository.enqueued.size)
        assertEquals(ticketCalls, repository.currentTicketCalls)
        assertEquals(0, repository.cancelCalls)
    }

    @Test
    fun resumingWhileTheDeleteIsInFlightDoesNotReenter() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.cancelGate = gate
        val vm = viewModel()
        vm.effects.test {
            vm.onCancel()
            vm.onResumed()
            assertEquals(1, repository.enqueued.size)

            gate.complete(Unit)

            assertEquals(MatchQueueEffect.Close, awaitItem())
            assertEquals(1, repository.enqueued.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun matchedVm(): MatchQueueViewModel {
        repository.enqueueResult = ApiResult.Success(matchedTicket("m1"))
        return viewModel()
    }

    @Test
    fun resumingMatchedWithAResolvedMatchReturnsToIdleWithoutEnqueuing() = runTest {
        val vm = matchedVm()
        vm.effects.test {
            assertEquals(MatchQueueEffect.OpenMatch("m1"), awaitItem())
            assertEquals(QueuePhase.Matched("m1"), vm.state.value.phase)
            repository.matchResults = mutableListOf(
                ApiResult.Success(
                    testMatch(status = MatchStatus.SIMULATION, myChoice = MatchChoice.SIMULATION)
                )
            )

            vm.onResumed()

            assertEquals(QueuePhase.Idle, vm.state.value.phase)
            assertEquals(1, repository.enqueued.size)
            // La escucha ya no devuelve el estado a "encontrado".
            repository.tickets.emit(matchedTicket("m1"))
            assertEquals(QueuePhase.Idle, vm.state.value.phase)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun resumingMatchedWithAnExpiredMatchReturnsToIdle() {
        val vm = matchedVm()
        repository.matchResults =
            mutableListOf(ApiResult.Success(testMatch(status = MatchStatus.EXPIRED)))

        vm.onResumed()

        assertEquals(QueuePhase.Idle, vm.state.value.phase)
        assertEquals(1, repository.enqueued.size)
    }

    @Test
    fun resumingMatchedWithAPendingMatchChangesNothing() {
        val vm = matchedVm()

        vm.onResumed()

        assertEquals(QueuePhase.Matched("m1"), vm.state.value.phase)
        assertEquals(1, repository.enqueued.size)
    }

    @Test
    fun resumingMatchedWithANetworkFailureChangesNothing() {
        val vm = matchedVm()
        repository.matchResults = mutableListOf(ApiResult.Failure(AppError.Network))

        vm.onResumed()

        assertEquals(QueuePhase.Matched("m1"), vm.state.value.phase)
        assertEquals(1, repository.enqueued.size)
    }

    @Test
    fun resumingMatchedDoesNotOverlapChecks() = runTest {
        val vm = matchedVm()
        val gate = CompletableDeferred<Unit>()
        repository.matchGate = gate
        repository.matchResults =
            mutableListOf(ApiResult.Success(testMatch(status = MatchStatus.EXPIRED)))
        val calls = repository.matchCalls

        vm.onResumed()
        vm.onResumed()

        assertEquals(calls + 1, repository.matchCalls)
        gate.complete(Unit)
        assertEquals(QueuePhase.Idle, vm.state.value.phase)
    }

    @Test
    fun searchFromIdleEnqueuesOnlyWhenTheUserAsks() {
        val vm = matchedVm()
        repository.matchResults =
            mutableListOf(ApiResult.Success(testMatch(status = MatchStatus.EXPIRED)))
        vm.onResumed()
        repository.enqueueResult = ApiResult.Success(waitingTicket())

        // Cambiar de modo en "listo para buscar" no busca.
        vm.onModeSelected(Mode.ESTUDIO)
        assertEquals(QueuePhase.Idle, vm.state.value.phase)
        assertEquals(1, repository.enqueued.size)

        vm.onSearch()

        assertEquals(listOf(Mode.AMISTAD, Mode.ESTUDIO), repository.enqueued)
        assertEquals(QueuePhase.Searching, vm.state.value.phase)
    }

    @Test
    fun resumingWithoutLiveSearchOutsideSearchingDoesNothing() {
        repository.enqueueResult = ApiResult.Failure(AppError.Network)
        val vm = viewModel()

        vm.onResumed()

        assertEquals(1, repository.enqueued.size)
        assertTrue(vm.state.value.phase is QueuePhase.Error)
    }

    @Test
    fun cancelFailureShowsError() {
        repository.cancelResult = ApiResult.Failure(AppError.Network)
        val vm = viewModel()

        vm.onCancel()

        assertEquals(QueuePhase.Error(AppError.Network), vm.state.value.phase)
    }

    @Test
    fun quotaExceededForRandomMatchOpensG2OfThatQuota() = runTest {
        val resets = Instant.parse("2026-11-05T05:00:00Z")
        repository.enqueueResult =
            ApiResult.Failure(AppError.QuotaExceeded(QuotaKind.RANDOM_MATCH, resets))

        val vm = viewModel()

        vm.effects.test {
            assertEquals(
                MatchQueueEffect.OpenLimit(
                    Paywall(PlusSources.limit(QuotaKind.RANDOM_MATCH, resets))
                ),
                awaitItem()
            )
        }
        assertEquals(QueuePhase.Searching, vm.state.value.phase)
    }

    @Test
    fun quotaExceededForOtherKindShowsTheErrorInsteadOfG2() = runTest {
        repository.enqueueResult =
            ApiResult.Failure(AppError.QuotaExceeded(QuotaKind.SIMULATION, null))

        val vm = viewModel()

        vm.effects.test { expectNoEvents() }
        assertTrue(vm.state.value.phase is QueuePhase.Error)
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

    @Test
    fun resumesLiveWaitingTicketWithoutPosting() {
        repository.currentTicketResult = ApiResult.Success(waitingTicket(Mode.ESTUDIO))

        val vm = viewModel(Mode.AMISTAD)

        assertTrue(repository.enqueued.isEmpty())
        assertEquals(Mode.ESTUDIO, vm.state.value.mode)
        assertEquals(QueuePhase.Searching, vm.state.value.phase)
    }

    @Test
    fun resumesMatchedTicketPendingChoiceAndOpensF2() = runTest {
        repository.currentTicketResult = ApiResult.Success(matchedTicket("m9"))
        val vm = viewModel()
        vm.effects.test {
            assertEquals(MatchQueueEffect.OpenMatch("m9"), awaitItem())
            assertTrue(repository.enqueued.isEmpty())
            assertEquals(QueuePhase.Matched("m9"), vm.state.value.phase)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun matchedTicketOfExpiredMatchEnqueuesAgain() {
        repository.currentTicketResult = ApiResult.Success(matchedTicket("m1"))
        repository.matchResults =
            mutableListOf(ApiResult.Success(testMatch(status = MatchStatus.EXPIRED)))

        val vm = viewModel()

        assertEquals(listOf(Mode.AMISTAD), repository.enqueued)
        assertEquals(QueuePhase.Searching, vm.state.value.phase)
    }

    @Test
    fun matchedTicketOfSimulationMatchEnqueuesAgain() {
        repository.currentTicketResult = ApiResult.Success(matchedTicket("m1"))
        repository.matchResults = mutableListOf(
            ApiResult.Success(
                testMatch(status = MatchStatus.SIMULATION, myChoice = MatchChoice.SIMULATION)
            )
        )

        val vm = viewModel()

        assertEquals(listOf(Mode.AMISTAD), repository.enqueued)
        assertEquals(QueuePhase.Searching, vm.state.value.phase)
    }

    @Test
    fun matchedTicketWithOwnChoiceEnqueuesAgain() {
        repository.currentTicketResult = ApiResult.Success(matchedTicket("m1"))
        repository.matchResults =
            mutableListOf(ApiResult.Success(testMatch(myChoice = MatchChoice.SCENARIO)))

        viewModel()

        assertEquals(listOf(Mode.AMISTAD), repository.enqueued)
    }

    @Test
    fun conflictOnEnqueueResumesTicketInsteadOfError() {
        repository.enqueueResult = ApiResult.Failure(AppError.Conflict)
        val live: ApiResult<TicketDto?> = ApiResult.Success(waitingTicket())
        // Primer GET (antes del POST): nada; tras el 409: el ticket vivo.
        val vm = MatchQueueViewModel(
            Mode.AMISTAD,
            object : MatchRepository by repository {
                var calls = 0
                override suspend fun currentTicket(): ApiResult<TicketDto?> =
                    if (calls++ == 0) ApiResult.Success(null) else live
            }
        )

        assertEquals(QueuePhase.Searching, vm.state.value.phase)
    }

    @Test
    fun conflictWithoutLiveTicketStillShowsError() {
        repository.enqueueResult = ApiResult.Failure(AppError.Conflict)

        assertEquals(QueuePhase.Error(AppError.Conflict), viewModel().state.value.phase)
    }

    @Test
    fun expiredTicketOnStartEnqueuesAgain() {
        repository.currentTicketResult =
            ApiResult.Success(TicketDto(TicketStatus.EXPIRED, Mode.AMISTAD, TEST_EXPIRES))

        viewModel()

        assertEquals(listOf(Mode.AMISTAD), repository.enqueued)
    }

    @Test
    fun errorPhaseLetsUserSwitchModeAndRetry() {
        repository.enqueueResult = ApiResult.Failure(AppError.Underage)
        val vm = viewModel(Mode.PAREJA)
        assertTrue(vm.state.value.phase is QueuePhase.Error)

        repository.enqueueResult = ApiResult.Success(waitingTicket(Mode.AMISTAD))
        vm.onModeSelected(Mode.AMISTAD)

        assertEquals(listOf(Mode.PAREJA, Mode.AMISTAD), repository.enqueued)
        assertEquals(QueuePhase.Searching, vm.state.value.phase)
    }

    @Test
    fun defaultModeIsLastUsedThenFirstActive() {
        repository.lastMode = Mode.PAREJA
        assertEquals(Mode.PAREJA, viewModel(null).state.value.mode)

        repository.lastMode = Mode.PAREJA
        val vm = MatchQueueViewModel(null, repository, activeModes = listOf(Mode.ESTUDIO))
        assertEquals(Mode.ESTUDIO, vm.state.value.mode)
        assertEquals(listOf(Mode.ESTUDIO), vm.state.value.activeModes)
    }

    @Test
    fun enqueuesOnlyInAnActiveModeWhenUserHasOnlyEstudio() {
        val vm = MatchQueueViewModel(
            initialMode = null,
            repository = repository,
            loadActiveModes = { listOf(Mode.ESTUDIO) }
        )

        assertEquals(listOf(Mode.ESTUDIO), repository.enqueued)
        assertEquals(Mode.ESTUDIO, vm.state.value.mode)
        assertEquals(listOf(Mode.ESTUDIO), vm.state.value.activeModes)
    }

    @Test
    fun routeModeNotActiveFallsBackToAnActiveOne() {
        val vm = MatchQueueViewModel(
            initialMode = Mode.PAREJA,
            repository = repository,
            loadActiveModes = { listOf(Mode.AMISTAD, Mode.ESTUDIO) }
        )

        assertEquals(listOf(Mode.AMISTAD), repository.enqueued)
        assertEquals(listOf(Mode.AMISTAD, Mode.ESTUDIO), vm.state.value.activeModes)
    }

    @Test
    fun unreadableModesKeepAllOptions() {
        val vm = MatchQueueViewModel(Mode.AMISTAD, repository, loadActiveModes = { null })

        assertEquals(listOf(Mode.AMISTAD), repository.enqueued)
        assertEquals(Mode.entries, vm.state.value.activeModes)
    }

    @Test
    fun ignoresTicketOfAnotherMode() = runTest {
        val vm = viewModel(Mode.ESTUDIO)
        vm.effects.test {
            repository.tickets.emit(matchedTicket("old", Mode.AMISTAD))

            expectNoEvents()
            assertEquals(QueuePhase.Searching, vm.state.value.phase)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun listenerFailureFallsBackToPollingTheTicket() = runTest {
        repository.failListener = true
        val vm = MatchQueueViewModel(Mode.AMISTAD, repository, pollIntervalMs = 100)
        vm.effects.test {
            repository.currentTicketResult = ApiResult.Success(matchedTicket("m5"))
            mainRule.dispatcher.scheduler.advanceTimeBy(150)

            assertEquals(MatchQueueEffect.OpenMatch("m5"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Fallo de 0.2.2: con el match del día ya resuelto, F1 decía "¡Encontramos a alguien!" y abría F2. */
    @Test
    fun resolvedMatchOnEnqueueShowsAlreadyUsedAndDoesNotOpenF2() = runTest {
        repository.enqueueResult = ApiResult.Success(matchedTicket("m1"))
        repository.matchResults = mutableListOf(
            ApiResult.Success(
                testMatch(status = MatchStatus.SIMULATION, myChoice = MatchChoice.SIMULATION)
            )
        )
        val vm = viewModel()
        vm.effects.test {
            assertEquals(QueuePhase.AlreadyUsed("m1"), vm.state.value.phase)
            // El listener repite el ticket: sigue "ya usado" y no abre F2.
            repository.tickets.emit(matchedTicket("m1"))
            expectNoEvents()
            assertEquals(QueuePhase.AlreadyUsed("m1"), vm.state.value.phase)
            // "Ver match" sí abre F2 a pedido.
            vm.onOpenMatchAgain()
            assertEquals(MatchQueueEffect.OpenMatch("m1"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun closeWithoutLiveSearchLeavesWithoutDelete() = runTest {
        repository.enqueueResult = ApiResult.Success(matchedTicket("m1"))
        repository.matchResults =
            mutableListOf(ApiResult.Success(testMatch(status = MatchStatus.EXPIRED)))
        repository.cancelResult = ApiResult.Failure(AppError.Network)
        val vm = viewModel()
        vm.effects.test {
            vm.onCancel()
            assertEquals(MatchQueueEffect.Close, awaitItem())
            assertEquals(0, repository.cancelCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun closeFromErrorLeaves() = runTest {
        repository.enqueueResult = ApiResult.Failure(AppError.Network)
        val vm = viewModel()
        vm.effects.test {
            assertTrue(vm.state.value.phase is QueuePhase.Error)
            vm.onCancel()
            assertEquals(MatchQueueEffect.Close, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private val resetsAt = Instant.fromEpochSeconds(0)

    @Test
    fun loadsTheRealDailyQuotaWithoutBlockingTheSearch() {
        val quota = Usage(used = 1, limit = 3, resetsAt = resetsAt)
        val vm = MatchQueueViewModel(Mode.AMISTAD, repository, loadDailyQuota = { quota })

        assertEquals(quota, vm.state.value.dailyQuota)
        assertEquals(listOf(Mode.AMISTAD), repository.enqueued)
    }

    @Test
    fun withoutQuotaDataTheStateKeepsNull() {
        val vm = MatchQueueViewModel(Mode.AMISTAD, repository, loadDailyQuota = { null })

        assertNull(vm.state.value.dailyQuota)
        assertEquals(listOf(Mode.AMISTAD), repository.enqueued)
    }

    @Test
    fun todayCountFollowsTheRealLimit() {
        assertEquals("1 de 1", MatchStrings.todayCount(Usage(0, 1, resetsAt)))
        assertEquals("2 de 3", MatchStrings.todayCount(Usage(1, 3, resetsAt)))
        assertEquals(
            MatchStrings.TODAY_UNLIMITED,
            MatchStrings.todayCount(Usage(4, null, resetsAt))
        )
        // F1 en "ya usado": el backend ya cobró el match, `used` lo incluye.
        assertEquals(
            "1 de 1",
            MatchStrings.todayCount(Usage(1, 1, resetsAt), alreadyCounted = true)
        )
        assertEquals(
            "2 de 3",
            MatchStrings.todayCount(Usage(2, 3, resetsAt), alreadyCounted = true)
        )
    }

    private fun advance(ms: Long) = with(mainRule.dispatcher.scheduler) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun coupleShowsNoOneHintAfter45SecondsWithoutCancelingTheTicket() {
        val vm = viewModel(Mode.PAREJA)

        advance(MatchQueueViewModel.NO_ONE_HINT_MS - 1)
        assertFalse(vm.state.value.noOneYet)
        advance(1)

        assertTrue(vm.state.value.noOneYet)
        assertEquals(QueuePhase.Searching, vm.state.value.phase)
        assertEquals(0, repository.cancelCalls)
    }

    @Test
    fun friendshipAndStudyNeverShowNoOneHint() {
        listOf(Mode.AMISTAD, Mode.ESTUDIO).forEach { mode ->
            val vm = viewModel(mode)
            advance(MatchQueueViewModel.NO_ONE_HINT_MS * 2)
            assertFalse(vm.state.value.noOneYet)
        }
    }

    @Test
    fun noOneHintDisappearsWhenAMatchArrives() = runTest {
        val vm = viewModel(Mode.PAREJA)
        vm.effects.test {
            advance(MatchQueueViewModel.NO_ONE_HINT_MS)
            assertTrue(vm.state.value.noOneYet)

            repository.tickets.emit(matchedTicket("m9", Mode.PAREJA))

            assertEquals(MatchQueueEffect.OpenMatch("m9"), awaitItem())
            assertFalse(vm.state.value.noOneYet)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun noOneHintDoesNotAppearIfAMatchArrivesBefore45Seconds() = runTest {
        val vm = viewModel(Mode.PAREJA)
        vm.effects.test {
            repository.tickets.emit(matchedTicket("m9", Mode.PAREJA))
            awaitItem()

            advance(MatchQueueViewModel.NO_ONE_HINT_MS * 2)

            assertFalse(vm.state.value.noOneYet)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun cancelingResetsTheNoOneHintAndTimer() {
        val vm = viewModel(Mode.PAREJA)
        advance(MatchQueueViewModel.NO_ONE_HINT_MS)
        assertTrue(vm.state.value.noOneYet)

        vm.onCancel()
        assertFalse(vm.state.value.noOneYet)

        // Vuelve a entrar (red de seguridad): el reloj arranca de cero.
        vm.onResumed()
        advance(MatchQueueViewModel.NO_ONE_HINT_MS - 1)
        assertFalse(vm.state.value.noOneYet)
        advance(1)
        assertTrue(vm.state.value.noOneYet)
    }

    @Test
    fun switchingModeResetsTheNoOneHint() {
        val vm = viewModel(Mode.PAREJA)
        advance(MatchQueueViewModel.NO_ONE_HINT_MS - 10_000)

        vm.onModeSelected(Mode.AMISTAD)
        advance(MatchQueueViewModel.NO_ONE_HINT_MS * 2)
        assertFalse(vm.state.value.noOneYet)

        // De vuelta a Pareja el conteo empieza otra vez desde cero.
        vm.onModeSelected(Mode.PAREJA)
        advance(MatchQueueViewModel.NO_ONE_HINT_MS - 1)
        assertFalse(vm.state.value.noOneYet)
        advance(1)
        assertTrue(vm.state.value.noOneYet)
    }
}
