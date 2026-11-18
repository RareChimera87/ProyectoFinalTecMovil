package me.santiagoreina.parcheu.feature.home

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import me.santiagoreina.parcheu.contract.HomeDto
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Progress
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.contract.SimulationDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.simulation.FakeSimulationRepository
import me.santiagoreina.parcheu.data.simulation.summary
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.navigation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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
            simulations,
            displayName = { name }
        )

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
    fun premiumErrorsLeaveTheRoutingToTheGlobalBus() = runTest {
        // E7.3: G1 y la G2 del cupo (también la de uso justo de ParcheU+) las abre GlobalErrorHandler;
        // C1 solo libera el botón, sin texto de error ni navegación propia.
        val errors =
            listOf(AppError.QuotaExceeded(QuotaKind.SIMULATION, null), AppError.PlusRequired)
        for (sample in listOf(HomeSamples.noneLeft, HomeSamples.plus)) {
            for (error in errors) {
                simulations.startResult = ApiResult.Failure(error)
                val vm = viewModel(ApiResult.Success(sample.home))
                vm.load()
                vm.onSimulate(HomeSamples.suggestions(plus = sample.home.plus).first())
                val state = vm.state.value as HomeUiState.Loaded
                assertNull(state.actionError)
                assertNull(state.startingId)
                assertNull(withTimeoutOrNull(1_000) { vm.effects.first() })
            }
        }
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

    private var clock = 1_000_000L

    private fun refreshingVm(repo: HomeRepository, pollMs: Long = 5_000L) =
        HomeViewModel(repo, simulations, { "Mariana" }, { clock }, pollMs)

    private val runningHome = HomeSamples.inProgress.home
    private val doneHome = HomeSamples.resultReady.home.copy(running = null)

    @Test
    fun resumeReloadsSilentlyOnlyAfterTheInterval() = runTest {
        val repo = FakeHomeRepository(ApiResult.Success(doneHome))
        val vm = refreshingVm(repo)
        vm.load()
        vm.onResumed()
        clock += 2_999
        vm.onResumed()
        assertEquals(1, repo.calls)
        clock += 1
        vm.onResumed()
        assertEquals(2, repo.calls)
        assertTrue(vm.state.value is HomeUiState.Loaded)
    }

    @Test
    fun resumeBeforeTheFirstLoadDoesNothing() = runTest {
        val repo = FakeHomeRepository(ApiResult.Success(doneHome))
        val vm = refreshingVm(repo)
        clock += 10_000
        vm.onResumed()
        assertEquals(0, repo.calls)
    }

    @Test
    fun resumeDoesNotOverlapAnInFlightLoad() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val repo =
            object : HomeRepository {
                override suspend fun home(): ApiResult<HomeDto> {
                    calls++
                    if (calls > 1) gate.await()
                    return ApiResult.Success(doneHome)
                }
            }
        val vm = refreshingVm(repo)
        vm.load()
        clock += 10_000
        vm.onResumed()
        clock += 10_000
        vm.onResumed()
        vm.load()
        assertEquals(2, calls)
        gate.complete(Unit)
        assertEquals(doneHome, (vm.state.value as HomeUiState.Loaded).home)
    }

    @Test
    fun failedResumeKeepsDataWithoutSpinnerOrError() = runTest {
        val repo =
            FakeHomeRepository(ApiResult.Success(doneHome), ApiResult.Failure(AppError.Network))
        val vm = refreshingVm(repo)
        vm.load()
        clock += 5_000
        vm.onResumed()
        assertEquals(2, repo.calls)
        assertEquals(doneHome, (vm.state.value as HomeUiState.Loaded).home)
    }

    /** Siempre pausa al final: un sondeo vivo haría que runTest avance el reloj virtual sin fin. */
    private fun pollTest(
        repo: FakeHomeRepository,
        body: suspend TestScope.(HomeViewModel, FakeHomeRepository) -> Unit
    ) = runTest {
        val vm = refreshingVm(repo)
        try {
            body(vm, repo)
        } finally {
            vm.onPaused()
        }
    }

    @Test
    fun pollsOnlyWhileRunningAndStopsWhenItFinishes() = pollTest(
        FakeHomeRepository(
            ApiResult.Success(runningHome),
            ApiResult.Success(runningHome),
            ApiResult.Success(doneHome)
        )
    ) { vm, repo ->
        vm.load()
        vm.onResumed()
        assertEquals(1, repo.calls)
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(1, repo.calls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, repo.calls)
        advanceTimeBy(5_000)
        runCurrent()
        // Terminó: la tarjeta pasa a "resultado listo" y el sondeo se detiene.
        assertEquals(3, repo.calls)
        assertNull((vm.state.value as HomeUiState.Loaded).home.running)
        assertEquals(1, (vm.state.value as HomeUiState.Loaded).home.ready.size)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(3, repo.calls)
    }

    @Test
    fun noPollingWithoutARunningSimulation() = pollTest(
        FakeHomeRepository(ApiResult.Success(doneHome))
    ) { vm, repo ->
        vm.load()
        vm.onResumed()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, repo.calls)
    }

    @Test
    fun pausingStopsThePollingAndResumingRestartsIt() = pollTest(
        FakeHomeRepository(ApiResult.Success(runningHome))
    ) { vm, repo ->
        vm.load()
        vm.onResumed()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(2, repo.calls)
        vm.onPaused()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, repo.calls)
        clock += 10_000
        vm.onResumed()
        assertEquals(3, repo.calls)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(4, repo.calls)
    }

    @Test
    fun pollingNeverStartsInTheBackground() = pollTest(
        FakeHomeRepository(ApiResult.Success(runningHome))
    ) { vm, repo ->
        vm.load()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, repo.calls)
    }

    @Test
    fun firstNameHandlesBlankNames() {
        assertEquals("Mari", firstNameOf("  Mari  Gómez "))
        assertNull(firstNameOf(null))
        assertNull(firstNameOf("   "))
    }
}
