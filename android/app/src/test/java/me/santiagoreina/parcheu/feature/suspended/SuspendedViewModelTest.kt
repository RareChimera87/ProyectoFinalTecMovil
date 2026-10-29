package me.santiagoreina.parcheu.feature.suspended

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.data.auth.FakeAuthRepository
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.navigation.DeleteAccount
import me.santiagoreina.parcheu.navigation.ExportData
import me.santiagoreina.parcheu.navigation.Onboarding
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Bloqueo por suspensión (A3 de la revisión de #109): la persona puede exportar, borrar o salir. */
class SuspendedViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun exportOpensDownloadMyData() = runTest {
        val auth = FakeAuthRepository(signedIn = true)
        val viewModel = SuspendedViewModel(auth)
        viewModel.effects.test {
            viewModel.onExportData()
            assertEquals(SuspendedEffect.Open(ExportData), awaitItem())
        }
        assertEquals(emptyList<String>(), auth.calls)
    }

    @Test
    fun deleteAccountOpensDeleteConfirmation() = runTest {
        val viewModel = SuspendedViewModel(FakeAuthRepository(signedIn = true))
        viewModel.effects.test {
            viewModel.onDeleteAccount()
            assertEquals(SuspendedEffect.Open(DeleteAccount), awaitItem())
        }
    }

    @Test
    fun signOutReturnsToLogin() = runTest {
        val auth = FakeAuthRepository(signedIn = true)
        val viewModel = SuspendedViewModel(auth)
        viewModel.effects.test {
            viewModel.onSignOut()
            assertEquals(SuspendedEffect.ReplaceAll(Onboarding(page = 2)), awaitItem())
        }
        assertEquals(listOf("signOut"), auth.calls)
    }
}
