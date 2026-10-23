package me.santiagoreina.parcheu.feature.deleteaccount

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.account.FakeAccountRepository
import me.santiagoreina.parcheu.data.auth.FakeAuthRepository
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.navigation.ExportData
import me.santiagoreina.parcheu.navigation.Onboarding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DeleteAccountViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val account = FakeAccountRepository()
    private val auth = FakeAuthRepository(signedIn = true)

    private fun viewModel() = DeleteAccountViewModel(account, auth)

    @Test
    fun withoutConfirmingNothingIsDeleted() {
        val vm = viewModel()
        assertFalse(vm.state.value.canDelete)
        vm.delete()
        assertEquals(emptyList<String>(), account.calls)
        assertEquals(emptyList<String>(), auth.calls)
    }

    @Test
    fun confirmingAndDeletingCallsTheApiThenSignsOut() {
        val vm = viewModel()
        vm.setConfirmed(true)
        assertTrue(vm.state.value.canDelete)
        vm.delete()
        assertEquals(listOf("delete"), account.calls)
        assertEquals(listOf("signOut"), auth.calls)
        assertTrue(vm.state.value.deleted)
        assertFalse(auth.signedIn)
    }

    @Test
    fun aFailedDeleteKeepsTheSessionAndShowsTheError() {
        account.deleteResult = ApiResult.Failure(AppError.Network)
        val vm = viewModel()
        vm.setConfirmed(true)
        vm.delete()
        assertEquals(AppError.Network, vm.state.value.error)
        assertFalse(vm.state.value.deleted)
        assertEquals(emptyList<String>(), auth.calls)
        assertTrue(vm.state.value.canDelete)
    }

    @Test
    fun doneReturnsToLoginWithoutHistory() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onDone()
            assertEquals(DeleteAccountEffect.ReplaceAll(Onboarding(page = 2)), awaitItem())
        }
    }

    @Test
    fun downloadFirstOpensExportDataAndPlayOpensSubscriptions() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onDownloadFirst()
            assertEquals(DeleteAccountEffect.Open(ExportData), awaitItem())
            vm.onManageSubscription()
            assertEquals(DeleteAccountEffect.OpenPlaySubscriptions, awaitItem())
        }
    }
}
