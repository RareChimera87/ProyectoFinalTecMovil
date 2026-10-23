package me.santiagoreina.parcheu.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import me.santiagoreina.parcheu.ParcheUApp
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** B1 llega como raíz con `replaceAll` (Splash, A4): no debe ofrecer cerrar, pues vaciaría la pila. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TwinQuestionsRootStackTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun b1AsRootCannotGoBack() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            ParcheUApp(navController = navController)
        }
        composeRule.runOnUiThread {
            NavControllerNavigator(navController).replaceAll(TwinQuestions)
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            assertFalse(NavControllerNavigator(navController).canGoBack())
        }
    }
}
