package me.santiagoreina.parcheu.push

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import me.santiagoreina.parcheu.ParcheUApp
import me.santiagoreina.parcheu.navigation.Chat
import me.santiagoreina.parcheu.navigation.Home
import me.santiagoreina.parcheu.navigation.NavControllerNavigator
import me.santiagoreina.parcheu.navigation.Onboarding
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El deep link pendiente se entrega con la sesión lista (Home en la pila), no solo desde una raíz de la NavBar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PendingDeepLinkDeliveryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun clear() {
        PendingDeepLink.route.value?.let(PendingDeepLink::consume)
    }

    @Test
    fun aPushForAnotherChatOpensWhileInsideAChat() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            ParcheUApp(navController = navController)
        }
        val navigator = { NavControllerNavigator(navController) }
        composeRule.runOnUiThread { navigator().replaceAll(Home) }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { navigator().navigate(Chat("c-1")) }
        composeRule.waitForIdle()

        PendingDeepLink.offer(Chat("c-2"))
        composeRule.waitForIdle()

        assertEquals(Chat("c-2"), navController.currentBackStackEntry!!.toRoute<Chat>())
        assertNull(PendingDeepLink.route.value)
    }

    @Test
    fun aPushBeforeLoginWaitsForTheSession() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            ParcheUApp(navController = navController, startDestination = Onboarding(page = 1))
        }
        PendingDeepLink.offer(Chat("c-2"))
        composeRule.waitForIdle()
        assertEquals(Chat("c-2"), PendingDeepLink.route.value)

        composeRule.runOnUiThread { NavControllerNavigator(navController).replaceAll(Home) }
        composeRule.waitForIdle()
        assertNull(PendingDeepLink.route.value)
    }
}
