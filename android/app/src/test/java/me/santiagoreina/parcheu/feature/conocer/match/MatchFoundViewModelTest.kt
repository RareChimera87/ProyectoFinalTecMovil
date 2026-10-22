package me.santiagoreina.parcheu.feature.conocer.match

import app.cash.turbine.test
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.contract.MatchStatus
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MatchFoundViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeMatchRepository()

    private fun viewModel() = MatchFoundViewModel("m1", repository, pollIntervalMs = 1_000)

    private fun MatchFoundViewModel.loaded() = state.value as MatchFoundUiState.Loaded

    @Test
    fun loadsMatchAndOffersBothCardsWhenScenarioAvailable() {
        val vm = viewModel()

        assertTrue(vm.loaded().showScenario)
        assertEquals(MatchChoice.SCENARIO, vm.loaded().selected)
    }

    @Test
    fun withoutScenarioOnlySimulationIsOfferedAndSelected() {
        repository.matchResults =
            mutableListOf(ApiResult.Success(testMatch(scenarioAvailable = false)))
        val vm = viewModel()

        assertFalse(vm.loaded().showScenario)
        assertEquals(MatchChoice.SIMULATION, vm.loaded().selected)
        vm.onSelect(MatchChoice.SCENARIO)
        assertEquals(MatchChoice.SIMULATION, vm.loaded().selected)
    }

    @Test
    fun demoProfileIsExposed() {
        repository.matchResults = mutableListOf(ApiResult.Success(testMatch(isDemo = true)))

        assertTrue(viewModel().loaded().match.other.isDemo)
    }

    @Test
    fun loadFailureShowsErrorAndRetryReloads() {
        repository.matchResults = mutableListOf(ApiResult.Failure(AppError.Network))
        val vm = viewModel()
        assertEquals(MatchFoundUiState.Error(AppError.Network), vm.state.value)

        repository.matchResults = mutableListOf(ApiResult.Success(testMatch()))
        vm.load()

        assertTrue(vm.state.value is MatchFoundUiState.Loaded)
    }

    @Test
    fun startWithSimulationOpensSimulation() = runTest {
        val vm = viewModel()
        vm.onSelect(MatchChoice.SIMULATION)
        vm.effects.test {
            vm.onStart()

            assertEquals(listOf(MatchChoice.SIMULATION), repository.chosen)
            assertEquals(MatchFoundEffect.OpenSimulation("m1"), awaitItem())
        }
    }

    @Test
    fun startWithScenarioOpensScenario() = runTest {
        repository.chooseResult = ApiResult.Success(
            testMatch(myChoice = MatchChoice.SCENARIO, status = MatchStatus.SCENARIO)
        )
        val vm = viewModel()
        vm.effects.test {
            vm.onStart()

            assertEquals(listOf(MatchChoice.SCENARIO), repository.chosen)
            assertEquals(MatchFoundEffect.OpenScenario("m1"), awaitItem())
        }
    }

    @Test
    fun waitingForOtherPollsUntilStatusChanges() = runTest {
        repository.chooseResult = ApiResult.Success(testMatch(myChoice = MatchChoice.SCENARIO))
        val vm = viewModel()
        vm.effects.test {
            vm.onStart()
            assertTrue(vm.loaded().waitingOther)
            repository.matchResults = mutableListOf(
                ApiResult.Success(
                    testMatch(myChoice = MatchChoice.SCENARIO, status = MatchStatus.SCENARIO)
                )
            )

            advanceTimeBy(1_100)
            assertEquals(MatchFoundEffect.OpenScenario("m1"), awaitItem())
        }
    }

    @Test
    fun conflictOnChoiceShowsActionErrorAndKeepsCards() {
        repository.chooseResult = ApiResult.Failure(AppError.Conflict)
        val vm = viewModel()

        vm.onStart()

        assertEquals(AppError.Conflict, vm.loaded().actionError)
        assertFalse(vm.loaded().submitting)
    }

    @Test
    fun expiredMatchShowsExpiredState() {
        repository.matchResults =
            mutableListOf(ApiResult.Success(testMatch(status = MatchStatus.EXPIRED)))

        assertTrue(viewModel().loaded().expired)
    }
}
