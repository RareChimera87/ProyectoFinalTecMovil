package me.santiagoreina.parcheu.feature.simulations

import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.Page
import me.santiagoreina.parcheu.contract.QuotaDto
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.contract.Usage
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.simulation.FakeSimulationRepository
import me.santiagoreina.parcheu.data.simulation.summary
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SimulationsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeSimulationRepository()

    private fun quota(used: Int, limit: Int?) = QuotaDto(
        simulations = Usage(used, limit, Instant.parse("2026-11-09T05:00:00Z")),
        randomMatch = Usage(0, 1, Instant.parse("2026-11-04T05:00:00Z")),
        fullReport = Usage(0, 1, Instant.parse("2026-12-01T05:00:00Z")),
        plus = limit == null,
    )

    @Test
    fun emptyListShowsRemainingSimulations() = runTest {
        repository.quotaResult = ApiResult.Success(quota(used = 0, limit = 5))
        val viewModel = SimulationsViewModel(repository)
        assertEquals(SimulationsUiState.Empty(available = 5), viewModel.state.value)
    }

    @Test
    fun emptyListWithoutQuotaOmitsTheCount() = runTest {
        val viewModel = SimulationsViewModel(repository)
        assertEquals(SimulationsUiState.Empty(available = null), viewModel.state.value)
    }

    @Test
    fun paginatesWithTheCursor() = runTest {
        val a = summary(id = "a", status = SimulationStatus.DONE, score = 80)
        val b = summary(id = "b", status = SimulationStatus.FAILED)
        repository.pages =
            mapOf(
                null to ApiResult.Success(Page(listOf(a), nextCursor = "c1")),
                "c1" to ApiResult.Success(Page(listOf(b))),
            )
        val viewModel = SimulationsViewModel(repository)
        viewModel.loadMore()

        val state = viewModel.state.value as SimulationsUiState.Loaded
        assertEquals(listOf("a", "b"), state.items.map { it.id })
        assertEquals(null, state.nextCursor)
    }

    @Test
    fun failureShowsErrorAndRetryRecovers() = runTest {
        repository.pages = mapOf(null to ApiResult.Failure(AppError.Network))
        val viewModel = SimulationsViewModel(repository)
        assertEquals(SimulationsUiState.Error(AppError.Network), viewModel.state.value)

        repository.pages = mapOf(null to ApiResult.Success(Page(listOf(summary()))))
        viewModel.load()
        assertTrue(viewModel.state.value is SimulationsUiState.Loaded)
    }
}
