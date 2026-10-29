package me.santiagoreina.parcheu.feature.results

import app.cash.turbine.test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.DecisionResult
import me.santiagoreina.parcheu.contract.DecisionStatus
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.results.FakeResultsRepository
import me.santiagoreina.parcheu.data.results.fullReport
import me.santiagoreina.parcheu.data.results.lockedReport
import me.santiagoreina.parcheu.data.results.octoberQuota
import me.santiagoreina.parcheu.data.results.resultSimulation
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** F7/F8 y la decisión mutua (E4.6). */
class ResultsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeResultsRepository()

    private fun viewModel() = ResultsViewModel("sim-1", repository)

    private fun ResultsViewModel.content() = (state.value as ResultsUiState.Loaded).content

    @Test
    fun freeReportIsLockedWithCountsAndMonths() = runTest {
        val content = viewModel().content()
        assertFalse(content.full)
        assertTrue(content.report.unlockAvailable)
        assertNull(content.report.reasons)
        assertEquals(3, content.report.reasonsCount)
        assertEquals("octubre", content.usedMonth)
        assertEquals("noviembre", content.resetMonth)
        assertEquals(DecisionState.PENDING, content.decision)
        assertEquals("Alguien de la UIS", content.displayName)
        assertFalse(content.plus)
    }

    @Test
    fun monthsUseBogotaTime() {
        // 1 de enero 00:00 en Bogotá = 05:00 UTC: el mes en curso es diciembre.
        assertEquals(
            "diciembre" to "enero",
            ResultsViewModel.monthsOf(Instant.parse("2027-01-01T05:00:00Z"))
        )
    }

    @Test
    fun demoBadgeComesFromTheSimulationSummary() = runTest {
        repository.simulationResult = ApiResult.Success(resultSimulation(isDemo = true))
        assertTrue(viewModel().content().isDemo)
    }

    @Test
    fun plusSeesFullReport() = runTest {
        repository.reportResult = ApiResult.Success(fullReport())
        repository.quotaResult = ApiResult.Success(octoberQuota(plus = true))
        val content = viewModel().content()
        assertTrue(content.full)
        assertTrue(content.plus)
    }

    @Test
    fun unlockShowsFullReport() = runTest {
        val viewModel = viewModel()
        viewModel.onUnlock()
        assertEquals(1, repository.unlockCalls)
        assertTrue(viewModel.content().full)
        assertEquals(2, viewModel.content().report.reasons!!.size)
    }

    @Test
    fun unlockWithoutQuotaMarksReportAsUsedAndLeavesG2ToTheGlobalBus() = runTest {
        repository.unlockResult =
            ApiResult.Failure(AppError.QuotaExceeded(QuotaKind.FULL_REPORT, null))
        val viewModel = viewModel()
        viewModel.effects.test {
            viewModel.onUnlock()
            expectNoEvents()
        }
        val content = viewModel.content()
        assertFalse(content.full)
        assertFalse(content.report.unlockAvailable)
        assertNull(content.notice)
    }

    @Test
    fun consumedReportIsNotUnlockable() = runTest {
        repository.reportResult = ApiResult.Success(lockedReport(unlockAvailable = false))
        val content = viewModel().content()
        assertFalse(content.report.unlockAvailable)
    }

    @Test
    fun connectWaitsForTheOtherPerson() = runTest {
        val viewModel = viewModel()
        viewModel.onConnect()
        assertEquals(listOf(true), repository.decisions)
        assertEquals(DecisionState.WAITING, viewModel.content().decision)
        // Una segunda pulsación no vuelve a decidir.
        viewModel.onConnect()
        assertEquals(1, repository.decisions.size)
    }

    @Test
    fun mutualConnectionOpensTheChatAndRevealsTheName() = runTest {
        repository.decideResult =
            ApiResult.Success(DecisionResult(DecisionStatus.CONNECTED, chatId = "chat-9"))
        val viewModel = viewModel()
        repository.reportResult = ApiResult.Success(
            lockedReport().copy(
                myDecision = true,
                otherDecided = true,
                otherName = "Laura",
                chatId = "chat-9"
            )
        )
        viewModel.effects.test {
            viewModel.onConnect()
            assertEquals(ResultsEffect.OpenChat("chat-9"), awaitItem())
        }
        val content = viewModel.content()
        assertEquals(DecisionState.CONNECTED, content.decision)
        assertEquals("Laura", content.displayName)
        assertEquals("chat-9", content.chatId)
    }

    @Test
    fun declineDoesNotNavigate() = runTest {
        repository.decideResult = ApiResult.Success(DecisionResult(DecisionStatus.DECLINED))
        val viewModel = viewModel()
        viewModel.effects.test {
            viewModel.onDecline()
            expectNoEvents()
        }
        assertEquals(listOf(false), repository.decisions)
        assertEquals(DecisionState.DECLINED, viewModel.content().decision)
    }

    @Test
    fun reopeningAfterConnectingShowsWaiting() = runTest {
        repository.reportResult = ApiResult.Success(lockedReport().copy(myDecision = true))
        assertEquals(DecisionState.WAITING, viewModel().content().decision)
    }

    @Test
    fun otherDecidedAloneNeverShowsDeclined() = runTest {
        repository.reportResult =
            ApiResult.Success(lockedReport().copy(myDecision = true, otherDecided = true))
        assertEquals(DecisionState.WAITING, viewModel().content().decision)
    }

    @Test
    fun conflictReloadsTheRealDecision() = runTest {
        repository.decideResult = ApiResult.Failure(AppError.Conflict)
        val viewModel = viewModel()
        repository.reportResult = ApiResult.Success(lockedReport().copy(myDecision = false))
        viewModel.onConnect()
        assertEquals(DecisionState.DECLINED, viewModel.content().decision)
        assertNull(viewModel.content().busy)
    }

    @Test
    fun transcriptOnlyWithFullReport() = runTest {
        val viewModel = viewModel()
        viewModel.onOpenTranscript()
        assertNull(viewModel.content().transcript)

        repository.reportResult = ApiResult.Success(fullReport())
        val full = viewModel()
        full.onOpenTranscript()
        assertTrue(full.content().transcript is TranscriptState.Loaded)
        full.onCloseTranscript()
        assertNull(full.content().transcript)
    }

    @Test
    fun seePlusOpensThePaywall() = runTest {
        val viewModel = viewModel()
        viewModel.effects.test {
            viewModel.onSeePlus()
            assertEquals(ResultsEffect.OpenPaywall, awaitItem())
        }
    }

    @Test
    fun loadErrorAndRetry() = runTest {
        repository.reportResult = ApiResult.Failure(AppError.Network)
        val viewModel = viewModel()
        assertEquals(ResultsUiState.Error(AppError.Network), viewModel.state.value)
        repository.reportResult = ApiResult.Success(lockedReport())
        viewModel.onRetry()
        assertTrue(viewModel.state.value is ResultsUiState.Loaded)
    }
}
