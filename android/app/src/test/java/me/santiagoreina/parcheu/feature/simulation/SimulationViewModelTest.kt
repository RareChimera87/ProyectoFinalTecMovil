package me.santiagoreina.parcheu.feature.simulation

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.Progress
import me.santiagoreina.parcheu.contract.SimulationDto
import me.santiagoreina.parcheu.contract.SimulationStatus
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.simulation.FakeLiveSource
import me.santiagoreina.parcheu.data.simulation.FakeSimulationRepository
import me.santiagoreina.parcheu.data.simulation.SimulationLiveSource
import me.santiagoreina.parcheu.data.simulation.SimulationSnapshot
import me.santiagoreina.parcheu.data.simulation.snapshot
import me.santiagoreina.parcheu.data.simulation.summary
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** F4/F5 (E4.5): progreso en vivo, respaldo REST a los 10 s, DONE y FAILED. */
class SimulationViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeSimulationRepository()
    private val live = FakeLiveSource()

    private val created = mutableListOf<SimulationViewModel>()

    private fun track(viewModel: SimulationViewModel) = viewModel.also { created += it }

    /**
     * Los bucles de escucha y consulta son infinitos: se cancelan antes de que runTest termine, porque al
     * terminar drena el planificador (compartido con Main) y nunca acabaría.
     */
    private fun runVmTest(body: suspend TestScope.() -> Unit): TestResult = runTest {
        try {
            body()
        } finally {
            created.forEach { it.viewModelScope.cancel() }
        }
    }

    private fun viewModel(id: String = "sim-1") = track(SimulationViewModel(id, repository, live))

    private fun running(state: SimulationUiState) = state as SimulationUiState.Running

    @Test
    fun mapsScenarioIndexAndTurnToSteps() = runVmTest {
        val viewModel = viewModel()
        live.flow.emit(snapshot(scenarioIndex = 1, turn = 4, of = 5))

        val state = running(viewModel.state.value)
        assertEquals(
            listOf(
                ScenarioStep(1, StepState.DONE),
                ScenarioStep(2, StepState.IN_PROGRESS),
                ScenarioStep(3, StepState.PENDING),
            ),
            state.steps,
        )
        assertEquals(2, state.currentScenario)
        assertEquals(60, state.percent)
        assertEquals("Laura", state.participants?.otherLabel)
    }

    @Test
    fun startsAtZeroPercent() = runVmTest {
        val viewModel = viewModel()
        live.flow.emit(snapshot(status = SimulationStatus.QUEUED))

        val state = running(viewModel.state.value)
        assertEquals(0, state.percent)
        assertEquals(StepState.IN_PROGRESS, state.steps.first().state)
    }

    @Test
    fun doneOpensResultsOnce() = runVmTest {
        val viewModel = viewModel()
        viewModel.effects.test {
            live.flow.emit(snapshot(scenarioIndex = 2, turn = 3))
            expectNoEvents()
            live.flow.emit(snapshot(status = SimulationStatus.DONE, scenarioIndex = 2, turn = 5))
            live.flow.emit(snapshot(status = SimulationStatus.DONE, scenarioIndex = 2, turn = 5))
            assertEquals(SimulationEffect.OpenResults("sim-1"), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun failedShowsF5() = runVmTest {
        val viewModel = viewModel()
        live.flow.emit(snapshot(status = SimulationStatus.FAILED, scenarioIndex = 1, turn = 2))

        val state = viewModel.state.value as SimulationUiState.Failed
        assertEquals(2, state.pausedAtScenario)
        assertTrue(state.canRetry)
    }

    @Test
    fun refundedIsOnlyTrueWhenSummaryCarriesIt() = runVmTest {
        repository.getResult =
            ApiResult.Success(
                SimulationDto(
                    summary(status = SimulationStatus.FAILED, refunded = true),
                    Progress(1, 2, 5),
                ),
            )
        val refunded = viewModel()
        live.flow.emit(snapshot(status = SimulationStatus.FAILED, scenarioIndex = 1))
        assertTrue((refunded.state.value as SimulationUiState.Failed).refunded)

        val notRefunded = FakeLiveSource()
        repository.getResult =
            ApiResult.Success(
                SimulationDto(
                    summary(status = SimulationStatus.FAILED, refunded = false),
                    Progress(1, 2, 5),
                ),
            )
        val other = track(SimulationViewModel("sim-1", repository, notRefunded))
        notRefunded.flow.emit(snapshot(status = SimulationStatus.FAILED, scenarioIndex = 1))
        assertFalse((other.state.value as SimulationUiState.Failed).refunded)
    }

    @Test
    fun silentListenerFallsBackToRestExactlyOnceAndKeepsListening() = runVmTest {
        repository.getResult =
            ApiResult.Success(
                SimulationDto(summary(), Progress(scenarioIndex = 0, turn = 2, of = 5)),
            )
        val viewModel = viewModel()

        advanceTimeBy(LISTENER_SILENCE_TIMEOUT - 1.milliseconds)
        runCurrent()
        assertEquals(0, repository.getCalls)
        assertTrue(viewModel.state.value is SimulationUiState.Connecting)

        advanceTimeBy(2.milliseconds)
        runCurrent()
        assertEquals(1, repository.getCalls)
        assertEquals(1, running(viewModel.state.value).currentScenario)

        // Sigue escuchando: un dato posterior del listener actualiza la pantalla sin otra consulta.
        live.flow.emit(snapshot(scenarioIndex = 2, turn = 1))
        assertEquals(3, running(viewModel.state.value).currentScenario)
        advanceTimeBy(LISTENER_SILENCE_TIMEOUT * 3)
        runCurrent()
        assertEquals(1, repository.getCalls)
    }

    @Test
    fun failingListenerResubscribesWithBackoffAndPollsRestUntilDone() = runVmTest {
        val failing = FailingLiveSource()
        repository.getResult =
            ApiResult.Success(
                SimulationDto(summary(), Progress(scenarioIndex = 0, turn = 1, of = 5)),
            )
        val viewModel = track(SimulationViewModel("sim-1", repository, failing))
        viewModel.effects.test {
            advanceTimeBy(LISTENER_SILENCE_TIMEOUT + 1.milliseconds)
            runCurrent()
            assertTrue(failing.subscriptions > 1)
            assertEquals(1, repository.getCalls)
            assertTrue(viewModel.state.value is SimulationUiState.Running)

            // El listener sigue roto: la API se consulta periodicamente y no se congela en "En curso".
            advanceTimeBy(REST_POLL_INTERVAL)
            runCurrent()
            assertTrue(repository.getCalls >= 2)

            repository.getResult =
                ApiResult.Success(
                    SimulationDto(summary(status = SimulationStatus.DONE), Progress(2, 5, 5)),
                )
            advanceTimeBy(REST_POLL_INTERVAL)
            runCurrent()
            assertEquals(SimulationEffect.OpenResults("sim-1"), awaitItem())

            // Estado final: ya no se consulta ni se resuscribe.
            val calls = repository.getCalls
            val subs = failing.subscriptions
            advanceTimeBy(LISTENER_RETRY_MAX * 4)
            runCurrent()
            assertEquals(calls, repository.getCalls)
            assertEquals(subs, failing.subscriptions)
        }
    }

    @Test
    fun listenerRecoversAfterErrorAndStopsPolling() = runVmTest {
        val flaky = FailingLiveSource(failFirst = 1)
        val viewModel = track(SimulationViewModel("sim-1", repository, flaky))
        advanceTimeBy(LISTENER_RETRY_INITIAL + 1.milliseconds)
        runCurrent()
        flaky.flow.emit(snapshot(scenarioIndex = 1, turn = 2))
        assertEquals(2, running(viewModel.state.value).currentScenario)
        val calls = repository.getCalls
        advanceTimeBy(REST_POLL_INTERVAL * 3)
        runCurrent()
        assertEquals(calls, repository.getCalls)
    }

    @Test
    fun listenerThatAnswersInTimeSkipsTheFallback() = runVmTest {
        val viewModel = viewModel()
        live.flow.emit(snapshot(scenarioIndex = 0, turn = 1))
        val callsAfterFirstSnapshot = repository.getCalls // solo el resumen (con quién se simula)

        advanceTimeBy(LISTENER_SILENCE_TIMEOUT * 2)
        runCurrent()
        assertEquals(callsAfterFirstSnapshot, repository.getCalls)
        assertTrue(viewModel.state.value is SimulationUiState.Running)
    }

    @Test
    fun leavingAndComingBackResumesTheCurrentState() = runVmTest {
        // "Salir" = soltar este ViewModel; la simulación sigue corriendo en el servidor.
        val first = viewModel()
        live.flow.emit(snapshot(scenarioIndex = 1, turn = 2))
        assertEquals(2, running(first.state.value).currentScenario)

        // El canal en vivo conserva el estado actual (como Firestore): un ViewModel nuevo lo retoma.
        live.flow.emit(snapshot(scenarioIndex = 2, turn = 1))
        val second = viewModel()
        assertEquals(3, running(second.state.value).currentScenario)
    }

    @Test
    fun silentListenerAndApiFailureShowsAnErrorThatCanBeReloaded() = runVmTest {
        repository.getResult = ApiResult.Failure(AppError.Network)
        val viewModel = viewModel()
        advanceTimeBy(LISTENER_SILENCE_TIMEOUT + 1.milliseconds)
        runCurrent()
        assertEquals(SimulationUiState.Error(AppError.Network), viewModel.state.value)

        repository.getResult = ApiResult.Success(SimulationDto(summary(), Progress(0, 1, 5)))
        viewModel.onReload()
        assertTrue(viewModel.state.value is SimulationUiState.Running)
    }

    @Test
    fun retryStartsANewSimulationWithTheSamePerson() = runVmTest {
        repository.getResult =
            ApiResult.Success(
                SimulationDto(
                    summary(status = SimulationStatus.FAILED, refunded = true),
                    Progress(1, 2, 5),
                ),
            )
        val viewModel = viewModel()
        live.flow.emit(snapshot(status = SimulationStatus.FAILED, scenarioIndex = 1))

        viewModel.effects.test {
            viewModel.onRetry()
            assertEquals(SimulationEffect.OpenSimulation("sim-2"), awaitItem())
        }
        assertEquals(listOf("user-2" to summary().mode), repository.startCalls)
    }

    @Test
    fun retryErrorKeepsF5AndShowsTheReason() = runVmTest {
        repository.getResult =
            ApiResult.Success(
                SimulationDto(summary(status = SimulationStatus.FAILED), Progress(1, 2, 5)),
            )
        repository.startResult = ApiResult.Failure(AppError.QuotaExceeded(null, null))
        val viewModel = viewModel()
        live.flow.emit(snapshot(status = SimulationStatus.FAILED, scenarioIndex = 1))
        viewModel.onRetry()

        val state = viewModel.state.value as SimulationUiState.Failed
        assertFalse(state.retrying)
        assertTrue(state.retryError is AppError.QuotaExceeded)
    }

    @Test
    fun anonymousOtherCannotBeRetried() = runVmTest {
        repository.getResult =
            ApiResult.Success(
                SimulationDto(
                    summary(status = SimulationStatus.FAILED, otherId = null),
                    Progress(1, 2, 5),
                ),
            )
        val viewModel = viewModel()
        live.flow.emit(snapshot(status = SimulationStatus.FAILED, scenarioIndex = 1))

        assertFalse((viewModel.state.value as SimulationUiState.Failed).canRetry)
        viewModel.onRetry()
        assertTrue(repository.startCalls.isEmpty())
    }

    @Test
    fun percentIsClampedAndDoneIsFull() {
        val over = SimulationSnapshot(SimulationStatus.RUNNING, Progress(2, 9, 5), null)
        assertEquals(100, progressPercent(over))
        val done = SimulationSnapshot(SimulationStatus.DONE, Progress(2, 5, 5), 80)
        assertEquals(100, progressPercent(done))
        assertTrue(scenarioSteps(done).all { it.state == StepState.DONE })
    }
}

/** Canal en vivo que lanza error al suscribirse (reglas, token vencido): las primeras [failFirst] veces, o siempre. */
private class FailingLiveSource(private val failFirst: Int = Int.MAX_VALUE) : SimulationLiveSource {
    val flow = MutableSharedFlow<SimulationSnapshot>(replay = 1, extraBufferCapacity = 8)
    var subscriptions = 0
        private set

    override fun observe(id: String): Flow<SimulationSnapshot> {
        subscriptions++
        return if (subscriptions <= failFirst) {
            kotlinx.coroutines.flow.flow { throw IllegalStateException("PERMISSION_DENIED") }
        } else {
            flow
        }
    }
}
