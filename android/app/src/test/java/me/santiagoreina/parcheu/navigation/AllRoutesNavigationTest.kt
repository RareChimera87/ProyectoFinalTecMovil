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
import me.santiagoreina.parcheu.feature.home.HomeTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cada ruta del inventario que sigue siendo stub (B0..H3, lista de chats, confirmaciones) se puede navegar
 * y muestra su stub. Las rutas ya implementadas ([implementedRoutes]) tienen sus propias pruebas.
 */
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
            // Se arranca en Home: el Splash real (E2.2) navegaría solo según la sesión. Desde E4.7 Home
            // es la pantalla real C1 (HomeScreenTest), así que se comprueba su raíz y no un stub.
            ParcheUApp(navController = navController, startDestination = Home)
        }
        composeRule.onNodeWithTag(HomeTags.ROOT).assertIsDisplayed()

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
        fun routes(): List<Array<Any>> = routeCatalog
            .filterNot { it.route::class in implementedRoutes }
            .map { arrayOf<Any>(it) }

        /**
         * Rutas cuya pantalla real ya existe: A1 y A2-A4 (E2.2, SplashNavigationTest), B0 y el bloqueo por
         * edad (E2.6, BasicProfileScreenTest y UnderageViewModelTest), B1-B2 (E2.4, TwinQuestions), B3-B5
         * (E2.5: Modes, Consent, TwinReady), D1-D5 (E3.4, ParcheViewModelTest), F1-F2 (E4.4,
         * MatchQueueViewModelTest), F4-F6 (E4.5, feature/simulation), F7-F8 e I3 (E4.6, feature/results),
         * lista de chats y F9 (E5.2), H2 con sus confirmaciones (E8.1, ReportScreenTest y
         * SafetyScreenshotTest), el borrado y 'Descargar mis datos' (E8.3, DeleteAccountScreenTest,
         * ExportDataRouteTest y AccountScreenshotTest), C1/I1 (E4.7, HomeScreenTest y HomeScreenshotTest),
         * C2 (E5.4, NotificationsRouteTest y NotificationsScreenshotTest), C5 (E6.1, InviteAppLinkTest,
         * InvitesScreenTest e InvitesScreenshotTest), D6/D7 (E6.3, FofScreenTest y FofScreenshotTest) y
         * E1-E3 (E6.2, feature/presentar: IntroduceViewModelTest, IntroResultViewModelTest,
         * IntroProposalViewModelTest y PresentarScreenshotTest), C3 (E9.1, ProfileViewModelTest,
         * ProfileScreenTest y ProfileScreenshotTest) y G1-G4/I4 (E7.3, feature/plus:
         * PlusNavigationTest, PlusScreenTest y PlusScreenshotTest).
         */
        val implementedRoutes =
            setOf(
                Splash::class,
                Onboarding::class,
                BasicProfile::class,
                Underage::class,
                TwinQuestions::class,
                Modes::class,
                Consent::class,
                TwinReady::class,
                Parche::class,
                FriendsOfFriends::class,
                MatchQueue::class,
                MatchFound::class,
                Simulation::class,
                Simulations::class,
                Results::class,
                ChatList::class,
                Chat::class,
                Report::class,
                DeleteAccount::class,
                ExportData::class,
                Home::class,
                Notifications::class,
                Introduce::class,
                IntroResult::class,
                IntroProposal::class,
                AddFriends::class,
                Profile::class,
                Paywall::class,
                PurchaseSuccess::class,
                ManageSubscription::class
            )
    }
}
