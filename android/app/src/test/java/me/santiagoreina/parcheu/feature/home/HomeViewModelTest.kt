package me.santiagoreina.parcheu.feature.home

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Progress
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.contract.SimulationDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.simulation.FakeSimulationRepository
import me.santiagoreina.parcheu.data.simulation.summary
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.navigation.Paywall
import me.santiagoreina.parcheu.navigation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val simulations = FakeSimulationRepository()

    private fun viewModel(vararg results: ApiResult<*>, name: String? = "Mariana Gómez") =
        @Suppress("UNCHECKED_CAST")
        HomeViewModel(
            FakeHomeRepository(
                *(results as Array<ApiResult<me.santiagoreina.parcheu.contract.HomeDto>>)
            ),
            simulations
        ) {
            name
        }

    @Test
    fun loadsHomeWithGreetingAndRunningProgress() = runTest {
        val home = HomeSamples.inProgress.home
        simulations.getResult =
            ApiResult.Success(SimulationDto(summary(id = home.running!!.id), Progress(1, 3, 5)))
        val vm = viewModel(ApiResult.Success(home))
        vm.load()

        val state = vm.state.value as HomeUiState.Loaded
        assertEquals(home, state.home)
        assertEquals("Mariana", state.firstName)
        // Escenario 2 de 3 con 3 de 5 turnos: (1 + 0,6) / 3 = 53 %.
        assertEquals(RunningProgress(percent = 53, scenario = 2), state.progress)
    }

    @Test
    fun errorOnFirstLoadAndRetry() = runTest {
        val vm =
            viewModel(
                ApiResult.Failure(AppError.Network),
                ApiResult.Success(HomeSamples.empty.home)
            )
        vm.load()
        assertEquals(HomeUiState.Error(AppError.Network), vm.state.value)
        vm.load()
        assertTrue((vm.state.value as HomeUiState.Loaded).isEmpty)
    }

    @Test
    fun refreshFailureKeepsTheLoadedContent() = runTest {
        val vm =
            viewModel(
                ApiResult.Success(HomeSamples.freeThreeOfFive.home),
                ApiResult.Failure(AppError.Network)
            )
        vm.load()
        vm.load()
        assertEquals(HomeSamples.freeThreeOfFive.home, (vm.state.value as HomeUiState.Loaded).home)
    }

    @Test
    fun simulateFromSuggestionOpensF4() = runTest {
        val vm = viewModel(ApiResult.Success(HomeSamples.freeThreeOfFive.home))
        vm.load()
        val suggestion = HomeSamples.suggestions(plus = false).first()
        vm.onSimulate(suggestion)

        assertEquals(listOf("u-sebas" to Mode.AMISTAD), simulations.startCalls)
        assertEquals(HomeEffect.Navigate(Simulation("sim-2")), vm.effects.first())
        assertNull((vm.state.value as HomeUiState.Loaded).startingId)
    }

    @Test
    fun simulateWithoutQuotaOpensThePaywall() = runTest {
        simulations.startResult =
            ApiResult.Failure(AppError.QuotaExceeded(QuotaKind.SIMULATION, null))
        val vm = viewModel(ApiResult.Success(HomeSamples.noneLeft.home))
        vm.load()
        vm.onSimulate(HomeSamples.suggestions(plus = false).first())
        assertEquals(HomeEffect.Navigate(Paywall(PAYWALL_SOURCE_HOME)), vm.effects.first())
    }

    @Test
    fun plusAtFairUseLimitStaysOnScreenInsteadOfThePaywall() = runTest {
        // ParcheU+ que llegó al uso justo diario (QUOTA_PLUS_DAILY_SIMULATIONS): el 429 no abre G1.
        simulations.startResult =
            ApiResult.Failure(AppError.QuotaExceeded(QuotaKind.SIMULATION, null))
        val vm = viewModel(ApiResult.Success(HomeSamples.plus.home))
        vm.load()
        vm.onSimulate(HomeSamples.suggestions(plus = true).first())
        val state = vm.state.value as HomeUiState.Loaded
        assertEquals(AppError.QuotaExceeded(QuotaKind.SIMULATION, null), state.actionError)
        assertNull(state.startingId)
        assertEquals(
            "Ya usaste tus simulaciones disponibles por ahora.",
            homeErrorMessage(state.actionError!!)
        )
        assertNull(withTimeoutOrNull(1_000) { vm.effects.first() })
    }

    @Test
    fun otherSimulateErrorsStayOnScreen() = runTest {
        simulations.startResult = ApiResult.Failure(AppError.Network)
        val vm = viewModel(ApiResult.Success(HomeSamples.freeThreeOfFive.home))
        vm.load()
        vm.onSimulate(HomeSamples.suggestions(plus = false).first())
        assertEquals(AppError.Network, (vm.state.value as HomeUiState.Loaded).actionError)
        vm.onDismissError()
        assertNull((vm.state.value as HomeUiState.Loaded).actionError)
    }

    @Test
    fun firstNameHandlesBlankNames() {
        assertEquals("Mari", firstNameOf("  Mari  Gómez "))
        assertNull(firstNameOf(null))
        assertNull(firstNameOf("   "))
    }
}
