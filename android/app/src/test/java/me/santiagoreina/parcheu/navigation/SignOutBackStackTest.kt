package me.santiagoreina.parcheu.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import me.santiagoreina.parcheu.data.auth.FakeAuthRepository
import me.santiagoreina.parcheu.data.auth.LOGIN_PAGE
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.modes.FakeModesRepository
import me.santiagoreina.parcheu.feature.modes.meDto
import me.santiagoreina.parcheu.feature.privacysettings.FakePrivacySettingsRepository
import me.santiagoreina.parcheu.feature.privacysettings.PrivacySettingsRoute
import me.santiagoreina.parcheu.feature.privacysettings.PrivacySettingsTags
import me.santiagoreina.parcheu.feature.privacysettings.PrivacySettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Criterio de E8.2 (Fakes): tras 'Cerrar sesión' en H3, A4 queda como única entrada del back stack (Home,
 * Perfil y H3 desaparecen) y atrás ya no tiene a dónde volver. Usa el navegador real sobre un NavHost mínimo
 * para no arrancar la red de la app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SignOutBackStackTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun signOutLeavesA4AsTheOnlyBackStackEntry() {
        val auth = FakeAuthRepository(signedIn = true)
        val viewModel =
            PrivacySettingsViewModel(FakePrivacySettingsRepository(), FakeModesRepository(), auth)
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            val navigator = NavControllerNavigator(navController)
            ParcheUTheme {
                NavHost(navController, startDestination = Home) {
                    composable<Home> { Text("home") }
                    composable<Profile> { Text("profile") }
                    composable<PrivacySettings> {
                        PrivacySettingsRoute(
                            it.toRoute(),
                            navigator,
                            viewModel = viewModel
                        )
                    }
                    composable<Onboarding> { Text("a4:${it.toRoute<Onboarding>().page}") }
                }
            }
        }
        composeRule.runOnUiThread {
            navController.navigate(Profile)
            navController.navigate(PrivacySettings)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(PrivacySettingsTags.SIGN_OUT).performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("signOut"), auth.calls)
        composeRule.runOnUiThread {
            val entries = navController.currentBackStack.value.filter {
                it.destination.route != null
            }
            assertEquals(
                listOf(Onboarding(page = LOGIN_PAGE)),
                entries.map { it.toRoute<Onboarding>() }
            )
            assertFalse(navController.popBackStack())
        }
    }
}
