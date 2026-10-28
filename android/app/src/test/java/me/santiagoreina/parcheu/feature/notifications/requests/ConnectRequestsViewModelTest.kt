package me.santiagoreina.parcheu.feature.notifications.requests

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.ConnectRequestsDto
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.navigation.Paywall
import me.santiagoreina.parcheu.navigation.Results
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ConnectRequestsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun viewModel(vararg results: ApiResult<ConnectRequestsDto>) =
        ConnectRequestsViewModel(FakeConnectRequestsRepository(*results))

    @Test
    fun startsHiddenUntilLoaded() {
        assertEquals(ConnectRequestsUiState.Hidden, viewModel().state.value)
    }

    @Test
    fun freeSeesOnlyTheCount() {
        val vm = viewModel(ApiResult.Success(ConnectRequestsSamples.free))
        vm.load()
        assertEquals(ConnectRequestsUiState.Locked(2), vm.state.value)
    }

    @Test
    fun plusSeesTheList() {
        val vm = viewModel(ApiResult.Success(ConnectRequestsSamples.plus))
        vm.load()
        assertEquals(
            ConnectRequestsUiState.Plus(
                listOf(ConnectRequestsSamples.first, ConnectRequestsSamples.second)
            ),
            vm.state.value
        )
    }

    @Test
    fun nothingPendingHidesTheSectionForFreeAndPlus() {
        val vm =
            viewModel(
                ApiResult.Success(ConnectRequestsDto(count = 0)),
                ApiResult.Success(ConnectRequestsDto(count = 0, items = emptyList()))
            )
        vm.load()
        assertEquals(ConnectRequestsUiState.Hidden, vm.state.value)
        vm.load()
        assertEquals(ConnectRequestsUiState.Hidden, vm.state.value)
    }

    @Test
    fun answeringOneTakesItOffTheListOnTheNextLoad() {
        val afterAnswer =
            ConnectRequestsDto(count = 1, items = listOf(ConnectRequestsSamples.second))
        val vm =
            viewModel(
                ApiResult.Success(ConnectRequestsSamples.plus),
                ApiResult.Success(afterAnswer)
            )
        vm.load()
        vm.load()
        assertEquals(
            ConnectRequestsUiState.Plus(listOf(ConnectRequestsSamples.second)),
            vm.state.value
        )
    }

    @Test
    fun aFailedRefreshKeepsWhatWasOnScreen() {
        val vm =
            viewModel(
                ApiResult.Success(ConnectRequestsSamples.plus),
                ApiResult.Failure(me.santiagoreina.parcheu.core.error.AppError.Network)
            )
        vm.load()
        val before = vm.state.value
        vm.load()
        assertEquals(before, vm.state.value)
    }

    @Test
    fun lockedItemOpensThePaywall() = runTest {
        val vm = viewModel(ApiResult.Success(ConnectRequestsSamples.free))
        vm.onOpenLocked()
        assertEquals(
            ConnectRequestsEffect.Navigate(Paywall(PAYWALL_SOURCE_CONNECT_REQUESTS)),
            vm.effects.first()
        )
    }

    @Test
    fun plusItemOpensItsResult() = runTest {
        val vm = viewModel(ApiResult.Success(ConnectRequestsSamples.plus))
        vm.onOpen(ConnectRequestsSamples.first)
        assertEquals(ConnectRequestsEffect.Navigate(Results("sim-1")), vm.effects.first())
    }
}
