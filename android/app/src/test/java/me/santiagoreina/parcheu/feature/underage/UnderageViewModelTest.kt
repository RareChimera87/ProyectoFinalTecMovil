package me.santiagoreina.parcheu.feature.underage

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.data.auth.FakeAuthRepository
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.navigation.DeleteAccount
import me.santiagoreina.parcheu.navigation.Onboarding
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UnderageViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun deleteAccountOpensDeleteConfirmation() = runTest {
        val viewModel = UnderageViewModel(FakeAuthRepository(signedIn = true))
        viewModel.effects.test {
            viewModel.onDeleteAccount()
            assertEquals(UnderageEffect.Open(DeleteAccount), awaitItem())
        }
    }

    @Test
    fun signOutReturnsToLogin() = runTest {
        val auth = FakeAuthRepository(signedIn = true)
        val viewModel = UnderageViewModel(auth)
        viewModel.effects.test {
            viewModel.onSignOut()
            assertEquals(UnderageEffect.ReplaceAll(Onboarding(page = 2)), awaitItem())
        }
        assertEquals(listOf("signOut"), auth.calls)
    }
}
