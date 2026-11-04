package me.santiagoreina.parcheu.feature.presentar

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.GraphDto
import me.santiagoreina.parcheu.contract.GraphStats
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NodeDto
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** E1 (E6.2): modo, 0/1/2 elegidos, límite de 2, cupo y errores al presentar. */
class IntroduceViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repo = FakeIntroductionsRepository()

    @Test
    fun startsInAmistadWithFriendsAndRemainingSimulations() {
        val vm = IntroduceViewModel(repo)
        val state = vm.state.value
        assertEquals(Mode.AMISTAD, state.mode)
        assertEquals(SAMPLE_FRIENDS, state.friends)
        assertEquals(3, state.simulationsLeft)
        assertEquals(listOf(Mode.AMISTAD), repo.friendCalls)
        assertFalse(state.canSubmit)
    }

    @Test
    fun choosesAtMostTwoAndCanUnselect() {
        val vm = IntroduceViewModel(repo)
        vm.onToggle("u-vale")
        assertEquals(listOf("u-vale"), vm.state.value.selected)
        assertFalse(vm.state.value.canSubmit)
        vm.onToggle("u-sebas")
        vm.onToggle("u-juli")
        assertEquals(listOf("u-vale", "u-sebas"), vm.state.value.selected)
        assertTrue(vm.state.value.canSubmit)
        assertFalse(vm.state.value.isSelectable("u-juli"))
        vm.onToggle("u-vale")
        assertEquals(listOf("u-sebas"), vm.state.value.selected)
        assertTrue(vm.state.value.isSelectable("u-juli"))
    }

    @Test
    fun changingModeReloadsFriendsAndClearsTheSelection() {
        val vm = IntroduceViewModel(repo)
        vm.onToggle("u-vale")
        vm.onMode(Mode.ESTUDIO)
        assertEquals(Mode.ESTUDIO, vm.state.value.mode)
        assertTrue(vm.state.value.selected.isEmpty())
        assertEquals(listOf(Mode.AMISTAD, Mode.ESTUDIO), repo.friendCalls)
        // Pareja nunca: ni siquiera se consulta.
        vm.onMode(Mode.PAREJA)
        assertEquals(Mode.ESTUDIO, vm.state.value.mode)
        assertEquals(2, repo.friendCalls.size)
    }

    @Test
    fun submitSendsTheTwoChosenAndOpensTheResult() = runTest {
        val vm = IntroduceViewModel(repo)
        vm.onToggle("u-vale")
        vm.onToggle("u-sebas")
        vm.onSubmit()
        assertEquals(listOf(Triple(Mode.AMISTAD, "u-vale", "u-sebas")), repo.introduceCalls)
        assertEquals(IntroduceEffect.OpenResult("intro-1"), vm.effects.first())
        assertEquals(2, vm.state.value.simulationsLeft)
    }

    @Test
    fun quotaExceededShowsTheLimitAndOffersPlus() = runTest {
        repo.introduceResult = ApiResult.Failure(AppError.QuotaExceeded(QuotaKind.SIMULATION, null))
        val vm = IntroduceViewModel(repo)
        vm.onToggle("u-vale")
        vm.onToggle("u-sebas")
        vm.onSubmit()
        assertTrue(vm.state.value.limitReached)
        assertEquals(IntroduceNotice.QuotaExceeded, vm.state.value.notice)
        assertFalse(vm.state.value.canSubmit)
        vm.onSeePlus()
        assertEquals(IntroduceEffect.OpenPaywall, vm.effects.first())
    }

    @Test
    fun limitReachedFromTheQuotaDisablesSubmit() {
        repo.quotaResult = ApiResult.Success(weekQuota(used = 5))
        val vm = IntroduceViewModel(repo)
        vm.onToggle("u-vale")
        vm.onToggle("u-sebas")
        assertTrue(vm.state.value.limitReached)
        vm.onSubmit()
        assertTrue(repo.introduceCalls.isEmpty())
    }

    @Test
    fun blockedOrForbiddenShowsTheErrorWithoutLeaving() {
        repo.introduceResult = ApiResult.Failure(AppError.Blocked)
        val vm = IntroduceViewModel(repo)
        vm.onToggle("u-vale")
        vm.onToggle("u-sebas")
        vm.onSubmit()
        assertEquals(IntroduceNotice.Failed(AppError.Blocked), vm.state.value.notice)
        assertFalse(vm.state.value.busy)
        vm.onDismissNotice()
        assertNull(vm.state.value.notice)
    }

    @Test
    fun friendsErrorCanBeRetried() {
        repo.friendsResult = { ApiResult.Failure(AppError.Network) }
        val vm = IntroduceViewModel(repo)
        assertEquals(AppError.Network, vm.state.value.friendsError)
        repo.friendsResult = { ApiResult.Success(SAMPLE_FRIENDS) }
        vm.onRetry()
        assertNull(vm.state.value.friendsError)
        assertEquals(SAMPLE_FRIENDS, vm.state.value.friends)
    }

    @Test
    fun theE1ListOnlyHasConnectedNamedFriendsOfTheMode() {
        // El ego-grafo ya excluye a bloqueados y borrados (servidor); E1 se queda con los
        // CONNECTED con nombre.
        fun node(
            id: String,
            state: EdgeState,
            mode: Mode = Mode.AMISTAD,
            anonymous: Boolean = false,
            affinity: Int = 50
        ) = NodeDto(
            id,
            id,
            id.take(1),
            affinity = affinity,
            mode = mode,
            state = state,
            anonymous = anonymous
        )
        val graph = GraphDto(
            me = node("me", EdgeState.CONNECTED),
            nodes = listOf(
                node("u-vale", EdgeState.CONNECTED, affinity = 90),
                node("u-sebas", EdgeState.CONNECTED, affinity = 60),
                node("a_simulado", EdgeState.SIMULATED, anonymous = true),
                node("u-pendiente", EdgeState.PENDING),
                node("u-estudio", EdgeState.CONNECTED, mode = Mode.ESTUDIO)
            ),
            edges = emptyList(),
            stats = GraphStats(2, 1, 1, 60)
        )
        val friends = RemoteIntroductionsRepository.friendsOf(graph, Mode.AMISTAD)
        assertEquals(listOf("u-vale", "u-sebas"), friends.map { it.id })
    }
}
