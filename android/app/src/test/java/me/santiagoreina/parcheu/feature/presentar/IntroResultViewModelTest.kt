package me.santiagoreina.parcheu.feature.presentar

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.IntroductionStatus
import me.santiagoreina.parcheu.contract.QuotaKind
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

/** E2 (E6.2): simulando → listo, regla del informe mensual, enviar y 'No por ahora'. */
@OptIn(ExperimentalCoroutinesApi::class)
class IntroResultViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repo = FakeIntroductionsRepository()

    private fun IntroResultViewModel.content() = (state.value as IntroResultUiState.Loaded).content

    @Test
    fun pollsWhileTheTwinsSimulateAndStopsWhenReady() {
        repo.introductionResults = ArrayDeque(
            listOf(
                ApiResult.Success(introducerView(IntroductionStatus.SIMULATING)),
                ApiResult.Success(introducerView(IntroductionStatus.SIMULATING))
            )
        )
        repo.introductionFallback = ApiResult.Success(introducerView(IntroductionStatus.READY))
        val vm = IntroResultViewModel("intro-1", repo, pollMillis = 1_000)
        assertEquals(IntroductionStatus.SIMULATING, vm.content().status)
        main.dispatcher.scheduler.advanceTimeBy(1_001)
        assertEquals(IntroductionStatus.SIMULATING, vm.content().status)
        main.dispatcher.scheduler.advanceTimeBy(1_000)
        assertEquals(IntroductionStatus.READY, vm.content().status)
        val calls = repo.introductionCalls
        main.dispatcher.scheduler.advanceTimeBy(5_000)
        assertEquals(calls, repo.introductionCalls)
    }

    @Test
    fun freeReportArrivesLockedAndTheMonthlyReportCanBeUsed() {
        val vm = IntroResultViewModel("intro-1", repo)
        val report = vm.content().introduction.report!!
        assertTrue(report.locked)
        assertNull(report.reasons)
        assertEquals("octubre", vm.content().usedMonth)
        vm.onUnlock()
        assertEquals(1, repo.unlockCalls)
        assertFalse(vm.content().introduction.report!!.locked)
        assertEquals(2, vm.content().introduction.report!!.reasons!!.size)
    }

    @Test
    fun monthlyReportAlreadyUsedOpensG2() = runTest {
        repo.unlockResult = ApiResult.Failure(AppError.QuotaExceeded(QuotaKind.FULL_REPORT, null))
        val vm = IntroResultViewModel("intro-1", repo)
        vm.effects.test {
            vm.onUnlock()
            assertEquals(
                IntroResultEffect.OpenLimit(
                    Paywall(PlusSources.limit(QuotaKind.FULL_REPORT, null))
                ),
                awaitItem()
            )
        }
        assertNull(vm.content().notice)
        assertFalse(vm.content().introduction.report!!.unlockAvailable)
    }

    @Test
    fun sendProposesToBoth() {
        val vm = IntroResultViewModel("intro-1", repo)
        vm.onSend()
        assertEquals(listOf(true), repo.respondCalls)
        assertEquals(IntroductionStatus.PROPOSED, vm.content().status)
        vm.onSend()
        assertEquals(1, repo.respondCalls.size)
    }

    @Test
    fun notNowDeclinesAndCloses() = runTest {
        repo.respondResult = { ApiResult.Success(introducerView(IntroductionStatus.DECLINED)) }
        val vm = IntroResultViewModel("intro-1", repo)
        vm.onSkip()
        assertEquals(listOf(false), repo.respondCalls)
        assertEquals(IntroResultEffect.Close, vm.effects.first())
    }

    @Test
    fun aConflictReloadsTheRealState() {
        repo.respondResult = { ApiResult.Failure(AppError.Conflict) }
        val vm = IntroResultViewModel("intro-1", repo)
        repo.introductionFallback = ApiResult.Success(introducerView(IntroductionStatus.PROPOSED))
        vm.onSend()
        assertEquals(IntroductionStatus.PROPOSED, vm.content().status)
        assertNull(vm.content().busy)
    }

    @Test
    fun failedSimulationOffersToTryAgain() = runTest {
        repo.introductionFallback = ApiResult.Success(introducerView(IntroductionStatus.FAILED))
        val vm = IntroResultViewModel("intro-1", repo)
        assertEquals(IntroductionStatus.FAILED, vm.content().status)
        vm.onSend()
        assertTrue(repo.respondCalls.isEmpty())
        vm.onIntroduceAgain()
        assertEquals(IntroResultEffect.IntroduceAgain, vm.effects.first())
    }

    @Test
    fun loadErrorCanBeRetried() {
        repo.introductionResults = ArrayDeque(listOf(ApiResult.Failure(AppError.Network)))
        val vm = IntroResultViewModel("intro-1", repo)
        assertEquals(IntroResultUiState.Error(AppError.Network), vm.state.value)
        vm.onRetry()
        assertEquals(IntroductionStatus.READY, vm.content().status)
    }
}
