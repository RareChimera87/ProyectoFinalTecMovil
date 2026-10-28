package me.santiagoreina.parcheu.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.parche.FakeGraphRepository
import me.santiagoreina.parcheu.feature.parche.ParcheRoute
import me.santiagoreina.parcheu.feature.parche.ParcheSamples
import me.santiagoreina.parcheu.feature.parche.ParcheUiState
import me.santiagoreina.parcheu.feature.parche.ParcheViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Criterio de E6.4 (D8 → D1 filtrado): 'Ver en el grafo' navega con [AppNavigator.navigateReplacing]
 * (popUpTo + launchSingleTop), que reutiliza la entrada de D1 y su ViewModel. Con el navegador real sobre
 * un NavHost mínimo comprueba que D1 acaba filtrado por la comunidad.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CommunityToGraphNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun viewInGraphFiltersReusedD1Entry() {
        val fake = FakeGraphRepository().apply {
            graphReturns(ApiResult.Success(ParcheSamples.content))
        }
        var parcheViewModel: ParcheViewModel? = null
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            val navigator = NavControllerNavigator(navController)
            ParcheUTheme {
                NavHost(navController, startDestination = Home) {
                    composable<Home> { Text("home") }
                    composable<Parche> {
                        val vm = viewModel { ParcheViewModel(fake) }
                        parcheViewModel = vm
                        ParcheRoute(it.toRoute(), navigator, viewModel = vm)
                    }
                    composable<Communities> { Text("communities") }
                }
            }
        }
        composeRule.runOnUiThread {
            navController.navigate(Parche())
        }
        composeRule.waitForIdle()
        assertEquals(listOf<String?>(null), fake.graphCalls)
        composeRule.runOnUiThread { navController.navigate(Communities) }
        composeRule.waitForIdle()
        val d1Entry = composeRule.runOnUiThread {
            navController.currentBackStack.value.first {
                it.destination.route?.contains("Parche") ==
                    true
            }
        }

        val vmBefore = parcheViewModel
        composeRule.runOnUiThread {
            NavControllerNavigator(navController)
                .navigateReplacing(Parche(community = "c1"), Communities::class)
        }
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            val stack = navController.currentBackStack.value.filter { it.destination.route != null }
            assertEquals(2, stack.size)
            assertTrue(stack.last().destination.route.orEmpty().contains("Parche"))
            val state = parcheViewModel!!.state.value as ParcheUiState.Loaded
            assertEquals("c1", state.filters.communityId)
        }
        assertTrue(vmBefore === parcheViewModel)
        assertEquals("c1", fake.graphCalls.last())
    }
}
