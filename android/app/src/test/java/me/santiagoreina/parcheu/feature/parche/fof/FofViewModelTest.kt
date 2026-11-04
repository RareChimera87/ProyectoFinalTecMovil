package me.santiagoreina.parcheu.feature.parche.fof

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.navigation.Paywall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Estados de D6/D7 y "Pedir presentación" (E6.3). */
class FofViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun plusShowsUnlockedList() {
        val vm = FofViewModel(FakeFofRepository())
        vm.start()
        assertEquals(FofUiState.Unlocked(FofSamples.plus), vm.state.value)
    }

    @Test
    fun freeShowsLockedWithOnlyTheCount() {
        val vm = FofViewModel(FakeFofRepository(ApiResult.Success(FofSamples.free)))
        vm.start()
        assertEquals(FofUiState.Locked(12), vm.state.value)
    }

    @Test
    fun failureShowsErrorAndRetryReloads() {
        val repo = FakeFofRepository(ApiResult.Failure(AppError.Network))
        val vm = FofViewModel(repo)
        vm.start()
        vm.start()
        assertEquals(FofUiState.Error(AppError.Network), vm.state.value)
        assertEquals(1, repo.loads)
        repo.loadResult = ApiResult.Success(FofSamples.plus)
        vm.retry()
        assertEquals(FofUiState.Unlocked(FofSamples.plus), vm.state.value)
    }

    @Test
    fun lockedReloadsOnResumeAndUnlocksAfterPurchase() {
        val repo = FakeFofRepository(ApiResult.Success(FofSamples.free))
        val vm = FofViewModel(repo)
        vm.start()
        assertEquals(FofUiState.Locked(12), vm.state.value)
        // Vuelve de G1 sin haber comprado: sigue bloqueado.
        vm.refreshIfLocked()
        assertEquals(FofUiState.Locked(12), vm.state.value)
        // Compró ParcheU+ en G1 y volvió con Atrás.
        repo.loadResult = ApiResult.Success(FofSamples.plus)
        vm.refreshIfLocked()
        assertEquals(FofUiState.Unlocked(FofSamples.plus), vm.state.value)
        assertEquals(3, repo.loads)
        // Ya desbloqueado, reanudar no vuelve a pedir nada.
        vm.refreshIfLocked()
        assertEquals(3, repo.loads)
    }

    @Test
    fun unlockOpensPaywall() = runTest {
        val vm = FofViewModel(FakeFofRepository(ApiResult.Success(FofSamples.free)))
        vm.start()
        vm.effects.test {
            vm.onUnlock()
            assertEquals(FofEffect.Navigate(Paywall(PAYWALL_SOURCE_FOF)), awaitItem())
        }
    }

    @Test
    fun requestIntroductionMarksTheCandidateAsRequestedOnce() {
        val repo = FakeFofRepository()
        val vm = FofViewModel(repo)
        vm.start()
        val anonId = FofSamples.candidates.first().anonId
        vm.onRequestIntroduction(anonId)
        vm.onRequestIntroduction(anonId)
        val state = vm.state.value as FofUiState.Unlocked
        assertEquals(setOf(anonId), state.requested)
        assertNull(state.sending)
        assertEquals(FofViewModel.REQUESTED_MESSAGE, state.message)
        assertEquals(listOf(anonId), repo.requested)
    }

    @Test
    fun failedRequestShowsMessageAndAllowsRetry() {
        val repo = FakeFofRepository().apply {
            requestResult =
                ApiResult.Failure(AppError.Unknown(501))
        }
        val vm = FofViewModel(repo)
        vm.start()
        val anonId = FofSamples.candidates.first().anonId
        vm.onRequestIntroduction(anonId)
        val state = vm.state.value as FofUiState.Unlocked
        assertEquals(emptySet<String>(), state.requested)
        assertEquals(AppError.Unknown(501).introMessage(), state.message)
        repo.requestResult = ApiResult.Success(Unit)
        vm.onRequestIntroduction(anonId)
        assertEquals(setOf(anonId), (vm.state.value as FofUiState.Unlocked).requested)
    }

    @Test
    fun plusRequiredOnRequestLeavesG1ToTheGlobalBus() = runTest {
        val repo = FakeFofRepository().apply {
            requestResult =
                ApiResult.Failure(AppError.PlusRequired)
        }
        val vm = FofViewModel(repo)
        vm.start()
        vm.effects.test {
            vm.onRequestIntroduction(FofSamples.candidates.first().anonId)
            // G1 la abre GlobalErrorHandler (origen `fof`); D6 solo deja su mensaje.
            expectNoEvents()
        }
        assertEquals(
            AppError.PlusRequired.introMessage(),
            (vm.state.value as FofUiState.Unlocked).message
        )
    }
}
