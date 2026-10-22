package me.santiagoreina.parcheu.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import me.santiagoreina.parcheu.ParcheUApp
import me.santiagoreina.parcheu.core.ui.stubTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/** Cada ruta del inventario (A1..H3, lista de chats, confirmaciones) se puede navegar y muestra su stub. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35])
class AllRoutesNavigationTest(private val entry: RouteEntry) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun navigatesToRouteAndShowsItsStub() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            ParcheUApp(navController = navController)
        }
        composeRule.onNodeWithTag(stubTag(Splash)).assertIsDisplayed()

        composeRule.runOnUiThread { navController.navigate(entry.route) }
        composeRule.waitForIdle()

        val tag = stubTag(entry.route)
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
        composeRule.onAllNodes(
            hasText(entry.title) and hasAnyAncestor(hasTestTag(tag))
        ).onFirst().assertIsDisplayed()
        // Los argumentos llegan intactos a la pantalla (round-trip de la ruta tipada).
        composeRule
            .onAllNodes(hasText(entry.route.toString()) and hasAnyAncestor(hasTestTag(tag)))
            .onFirst()
            .assertIsDisplayed()
        val shown = navController.currentBackStackEntry?.destination
        assertEquals(
            "NavBar solo en las raíces",
            TopLevelDestination.entries.any { it.routeClass == entry.route::class },
            shown.isTopLevel()
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun routes(): List<Array<Any>> = routeCatalog.map { arrayOf<Any>(it) }
    }
}
