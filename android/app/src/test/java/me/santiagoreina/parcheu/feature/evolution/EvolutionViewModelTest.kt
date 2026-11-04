package me.santiagoreina.parcheu.feature.evolution

import app.cash.turbine.test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.HistoryDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.navigation.Paywall
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Estados de D9 (E6.5). */
class EvolutionViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun historyShowsContent() {
        val vm = EvolutionViewModel(FakeEvolutionRepository())
        vm.start()
        assertEquals(EvolutionUiState.Content(EvolutionSamples.full), vm.state.value)
    }

    @Test
    fun lessThanTwoMonthsShowsTooEarly() {
        val vm =
            EvolutionViewModel(
                FakeEvolutionRepository(ApiResult.Success(EvolutionSamples.tooEarly))
            )
        vm.start()
        assertEquals(EvolutionUiState.TooEarly(EvolutionSamples.tooEarly), vm.state.value)
        val empty = EvolutionViewModel(FakeEvolutionRepository(ApiResult.Success(HistoryDto())))
        empty.start()
        assertEquals(EvolutionUiState.TooEarly(HistoryDto()), empty.state.value)
    }

    @Test
    fun plusRequiredShowsLockedAndUnlockOpensPaywall() = runTest {
        val vm =
            EvolutionViewModel(FakeEvolutionRepository(ApiResult.Failure(AppError.PlusRequired)))
        vm.start()
        assertEquals(EvolutionUiState.Locked, vm.state.value)
        vm.navigation.test {
            vm.onUnlock()
            assertEquals(Paywall(PAYWALL_SOURCE_EVOLUTION), awaitItem())
        }
    }

    @Test
    fun failureShowsErrorAndRetryReloads() {
        val repo = FakeEvolutionRepository(ApiResult.Failure(AppError.Network))
        val vm = EvolutionViewModel(repo)
        vm.start()
        vm.start()
        assertEquals(EvolutionUiState.Error(AppError.Network), vm.state.value)
        assertEquals(1, repo.loads)
        repo.result = ApiResult.Success(EvolutionSamples.full)
        vm.retry()
        assertEquals(EvolutionUiState.Content(EvolutionSamples.full), vm.state.value)
    }

    @Test
    fun lockedReloadsOnResumeAfterPurchase() {
        val repo = FakeEvolutionRepository(ApiResult.Failure(AppError.PlusRequired))
        val vm = EvolutionViewModel(repo)
        vm.start()
        repo.result = ApiResult.Success(EvolutionSamples.full)
        vm.refreshIfLocked()
        assertEquals(EvolutionUiState.Content(EvolutionSamples.full), vm.state.value)
        vm.refreshIfLocked()
        assertEquals(2, repo.loads)
    }

    @Test
    fun tappingAMonthSelectsItAndTappingAgainClearsIt() {
        val vm = EvolutionViewModel(FakeEvolutionRepository())
        vm.start()
        vm.onMonth("2026-08")
        assertEquals(EvolutionUiState.Content(EvolutionSamples.full, "2026-08"), vm.state.value)
        vm.onMonth("2026-08")
        assertEquals(EvolutionUiState.Content(EvolutionSamples.full, null), vm.state.value)
    }

    @Test
    fun monthNamesAndBogotaMonth() {
        assertEquals("Julio", monthName("2026-07"))
        assertEquals("2026-xx", monthName("2026-xx"))
        // 1 nov 2026 02:00 UTC es todavía 31 de octubre en Bogotá.
        assertEquals("Oct", shortMonthOf(Instant.parse("2026-11-01T02:00:00Z")))
        assertEquals("Nov", shortMonthOf(Instant.parse("2026-11-01T06:00:00Z")))
        assertEquals("Ene", shortMonthOf(Instant.parse("2026-01-15T12:00:00Z")))
        assertEquals("Dic", shortMonthOf(Instant.parse("2026-12-31T12:00:00Z")))
    }
}
