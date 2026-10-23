package me.santiagoreina.parcheu.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import me.santiagoreina.parcheu.ParcheUApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tras `replaceAll(Home)`, cambiar de pestaña no apila entradas: las raíces no tienen "Volver". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TopLevelStackTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tabSwitchesDoNotGrowBackStackAfterReplaceAll() {
        lateinit var navController: NavHostController
        lateinit var navigator: AppNavigator
        composeRule.setContent {
            navController = rememberNavController()
            // Desde E2.2 el Splash decide el destino solo; se arranca en Home para no competir con él.
            ParcheUApp(navController = navController, startDestination = Home)
        }
        composeRule.runOnUiThread {
            navigator = NavControllerNavigator(navController)
            navigator.replaceAll(Home)
        }
        composeRule.waitForIdle()

        repeat(3) {
            listOf(Parche(), ChatList, Home).forEach { tab ->
                composeRule.runOnUiThread { navigator.navigateToTopLevel(tab) }
                composeRule.waitForIdle()
                assertFalse("Raíz sin Volver: $tab", navigator.canGoBack())
            }
        }

        val size = navController.currentBackStack.value.count { it.destination.route != null }
        // Home (raíz) + la pestaña actual: nunca se acumulan ciclos viejos.
        assertEquals("La pila no crece con las pestañas", 1, size)
    }
}
