package me.santiagoreina.parcheu.feature.conocer.scenario

import app.cash.turbine.test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.ScenarioSessionDto
import me.santiagoreina.parcheu.contract.ScenarioSessionStatus
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FakeScenarioRepository(
    var session: ApiResult<ScenarioSessionDto>,
    var agree: ApiResult<ScenarioSessionDto>? = null
) : ScenarioRepository {
    var agreeCalls = 0
    var sessionCalls = 0

    override suspend fun session(chatId: String): ApiResult<ScenarioSessionDto> {
        sessionCalls++
        return session
    }

    override suspend fun agree(chatId: String): ApiResult<ScenarioSessionDto> {
        agreeCalls++
        return agree ?: session
    }
}

class ScenarioViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val t0 = Instant.fromEpochSeconds(1_000)

    private fun make(
        repo: ScenarioRepository,
        now: () -> Instant = {
            t0
        }
    ) = ScenarioViewModel("c1", repo, now)

    private fun session(
        status: ScenarioSessionStatus = ScenarioSessionStatus.OPEN,
        simulationId: String? = null,
        iAgreed: Boolean = false,
        otherAgreed: Boolean = false
    ) = ScenarioSessionDto(
        status = status,
        simulationId = simulationId,
        title = "Planear el fin de semana",
        prompt = "Pónganse de acuerdo.",
        budgetCop = 80_000,
        endsAt = Instant.fromEpochSeconds(1_600),
        iAgreed = iAgreed,
        otherAgreed = otherAgreed
    )

    @Test
    fun loadsSessionAndComputesRemainingTime() = runTest {
        val vm =
            make(FakeScenarioRepository(ApiResult.Success(session())), now = {
                t0
            })
        val state = vm.state.value
        assertEquals(600, state.remainingSec)
        assertEquals(600, state.totalSec)
        assertTrue(state.canAgree)
        assertEquals("10:00", ScenarioStrings.clock(state.remainingSec))
    }

    @Test
    fun tickCountsDownAndBlocksAgreementWhenTimeIsUp() = runTest {
        var now = t0
        val vm = make(FakeScenarioRepository(ApiResult.Success(session())), now = { now })
        now = Instant.fromEpochSeconds(1_599)
        vm.onTick()
        assertEquals(1, vm.state.value.remainingSec)
        assertTrue(vm.state.value.canAgree)
        now = Instant.fromEpochSeconds(1_700)
        vm.onTick()
        assertEquals(0, vm.state.value.remainingSec)
        assertFalse(vm.state.value.canAgree)
    }

    @Test
    fun agreeWithOnlyMeShowsWaitingOther() = runTest {
        val repo = FakeScenarioRepository(
            ApiResult.Success(session()),
            agree = ApiResult.Success(session(ScenarioSessionStatus.WAITING_OTHER, iAgreed = true))
        )
        val vm = make(repo, now = { t0 })
        vm.onAgree()
        assertEquals(1, repo.agreeCalls)
        assertEquals(ScenarioSessionStatus.WAITING_OTHER, vm.state.value.status)
        assertFalse(vm.state.value.canAgree)
        // No se puede volver a proponer.
        vm.onAgree()
        assertEquals(1, repo.agreeCalls)
    }

    @Test
    fun agreeAfterTimeEndedShowsErrorAndRefreshes() = runTest {
        val repo =
            FakeScenarioRepository(
                ApiResult.Success(session()),
                agree = ApiResult.Failure(AppError.ScenarioEnded)
            )
        val vm = make(repo, now = { t0 })
        val before = repo.sessionCalls
        vm.onAgree()
        assertEquals(AppError.ScenarioEnded, vm.state.value.actionError)
        assertTrue(repo.sessionCalls > before)
    }

    @Test
    fun pollingPicksUpTheOtherPersonAgreementAndOpensResultsWhenDone() = runTest {
        val repo = FakeScenarioRepository(ApiResult.Success(session()))
        val vm = make(repo)
        vm.effects.test {
            repo.session = ApiResult.Success(session(otherAgreed = true))
            vm.poll()
            assertTrue(vm.state.value.session!!.otherAgreed)
            repo.session =
                ApiResult.Success(
                    session(ScenarioSessionStatus.JUDGING, iAgreed = true, otherAgreed = true)
                )
            vm.poll()
            assertTrue(vm.state.value.finished)
            repo.session =
                ApiResult.Success(session(ScenarioSessionStatus.DONE, simulationId = "sim-1"))
            vm.poll()
            assertEquals(ScenarioEffect.OpenResults("sim-1"), awaitItem())
            // DONE ya no se vuelve a consultar ni a abrir.
            val calls = repo.sessionCalls
            vm.poll()
            assertEquals(calls, repo.sessionCalls)
            expectNoEvents()
        }
    }

    @Test
    fun doneWithoutResultStaysOnTheChat() = runTest {
        val vm =
            ScenarioViewModel(
                "c1",
                FakeScenarioRepository(
                    ApiResult.Success(session(ScenarioSessionStatus.DONE))
                ),
                now = {
                    t0
                }
            )
        vm.effects.test { expectNoEvents() }
        assertEquals(ScenarioSessionStatus.DONE, vm.state.value.status)
    }

    @Test
    fun loadFailureKeepsErrorUntilNextPoll() = runTest {
        val repo = FakeScenarioRepository(ApiResult.Failure(AppError.Network))
        val vm = make(repo, now = { t0 })
        assertEquals(AppError.Network, vm.state.value.loadError)
        repo.session = ApiResult.Success(session())
        vm.refresh()
        assertEquals(null, vm.state.value.loadError)
    }
}
