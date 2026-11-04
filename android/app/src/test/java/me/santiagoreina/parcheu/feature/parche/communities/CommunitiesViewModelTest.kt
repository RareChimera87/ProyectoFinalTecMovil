package me.santiagoreina.parcheu.feature.parche.communities

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.navigation.Parche
import me.santiagoreina.parcheu.navigation.Paywall
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Estados de D8 y sus salidas (E6.4). */
class CommunitiesViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun plusWithCommunitiesIsLoaded() {
        val vm = CommunitiesViewModel(FakeCommunitiesRepository())
        vm.start()
        assertEquals(CommunitiesUiState.Loaded(CommunitiesSamples.plus), vm.state.value)
    }

    @Test
    fun plusWithoutCommunitiesIsEmpty() {
        val vm =
            CommunitiesViewModel(
                FakeCommunitiesRepository(ApiResult.Success(CommunitiesSamples.plusEmpty))
            )
        vm.start()
        assertEquals(CommunitiesUiState.Empty, vm.state.value)
    }

    @Test
    fun freeIsLockedWithOnlyTheCount() {
        val vm =
            CommunitiesViewModel(
                FakeCommunitiesRepository(ApiResult.Success(CommunitiesSamples.free))
            )
        vm.start()
        assertEquals(CommunitiesUiState.Locked(2), vm.state.value)
    }

    @Test
    fun failureShowsErrorAndRetryReloads() {
        val repo = FakeCommunitiesRepository(ApiResult.Failure(AppError.Network))
        val vm = CommunitiesViewModel(repo)
        vm.start()
        vm.start()
        assertEquals(CommunitiesUiState.Error(AppError.Network), vm.state.value)
        assertEquals(1, repo.loads)
        repo.loadResult = ApiResult.Success(CommunitiesSamples.plus)
        vm.retry()
        assertEquals(CommunitiesUiState.Loaded(CommunitiesSamples.plus), vm.state.value)
    }

    @Test
    fun lockedUnlocksAfterPurchaseOnResume() {
        val repo = FakeCommunitiesRepository(ApiResult.Success(CommunitiesSamples.free))
        val vm = CommunitiesViewModel(repo)
        vm.start()
        vm.refreshIfLocked()
        assertEquals(CommunitiesUiState.Locked(2), vm.state.value)
        repo.loadResult = ApiResult.Success(CommunitiesSamples.plus)
        vm.refreshIfLocked()
        assertEquals(CommunitiesUiState.Loaded(CommunitiesSamples.plus), vm.state.value)
        vm.refreshIfLocked()
        assertEquals(3, repo.loads)
    }

    @Test
    fun unlockOpensThePaywallAndViewInGraphOpensD1Filtered() = runTest {
        val vm = CommunitiesViewModel(FakeCommunitiesRepository())
        vm.effects.test {
            vm.onUnlock()
            assertEquals(
                CommunitiesEffect.Navigate(Paywall(PAYWALL_SOURCE_COMMUNITIES)),
                awaitItem()
            )
            vm.onViewInGraph("c1")
            assertEquals(
                CommunitiesEffect.Navigate(Parche(community = "c1"), replaceCommunities = true),
                awaitItem()
            )
        }
    }
}
