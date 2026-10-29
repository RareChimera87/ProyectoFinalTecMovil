package me.santiagoreina.parcheu.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.error.GlobalErrorBus
import me.santiagoreina.parcheu.data.auth.FakeAuthRepository
import me.santiagoreina.parcheu.data.auth.LOGIN_PAGE
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.parche.PAYWALL_SOURCE_PARCHE
import me.santiagoreina.parcheu.feature.plus.PlusSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Criterio del hallazgo A-M2 (Fakes + Robolectric): el bus global saca a la persona de una pantalla
 * interior hacia A4 sin historial ante una sesión vencida, y abre G1 o G2 ante un 402. Usa el navegador
 * real sobre un NavHost mínimo, igual que [SignOutBackStackTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GlobalErrorHandlerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val auth = FakeAuthRepository(signedIn = true)
    private val bus = GlobalErrorBus(hasSession = auth::hasSession)
    private lateinit var navController: NavHostController

    private fun launch(start: AppRoute = Home, vararg above: AppRoute) {
        composeRule.setContent {
            navController = rememberNavController()
            val navigator = remember { NavControllerNavigator(navController) }
            val snackbar = remember { SnackbarHostState() }
            ParcheUTheme {
                Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
                    NavHost(
                        navController,
                        startDestination = start,
                        modifier = Modifier.padding(padding)
                    ) {
                        composable<Splash> { Text("splash") }
                        composable<Home> { Text("home") }
                        composable<Parche> { Text("parche") }
                        composable<Simulation> { Text("simulation") }
                        composable<Paywall> { Text("paywall") }
                        composable<Onboarding> { Text("a4:${it.toRoute<Onboarding>().page}") }
                    }
                    GlobalErrorHandler(
                        bus = bus,
                        navController = navController,
                        navigator = navigator,
                        snackbar = snackbar,
                        signOut = auth::signOut
                    )
                }
            }
        }
        composeRule.runOnUiThread { above.forEach { navController.navigate(it) } }
        composeRule.waitForIdle()
    }

    private fun entries(): List<NavBackStackEntry> =
        navController.currentBackStack.value.filter { it.destination.route != null }

    @Test
    fun unauthorizedFromAnInnerScreenSignsOutAndLeavesOnlyA4() {
        launch(Home, Parche(), Simulation("sim-1"))

        // Varias llamadas fallan a la vez al vencer la sesión: se cierra y se navega una sola vez.
        repeat(3) { bus.publish(AppError.Unauthorized) }
        composeRule.waitForIdle()

        assertEquals(listOf("signOut"), auth.calls)
        composeRule.runOnUiThread {
            assertEquals(
                listOf(Onboarding(page = LOGIN_PAGE)),
                entries().map { it.toRoute<Onboarding>() }
            )
            assertFalse(navController.popBackStack())
        }
        composeRule.onNodeWithText("Tu sesión terminó. Vuelve a iniciar sesión para seguir.")
            .assertExists()
    }

    @Test
    fun suspendedAccountAlsoSignsOutWithItsOwnMessage() {
        launch(Home, Parche())

        bus.publish(AppError.Suspended)
        composeRule.waitForIdle()

        assertEquals(listOf("signOut"), auth.calls)
        composeRule.runOnUiThread {
            assertEquals(1, entries().size)
            assertEquals(Onboarding(page = LOGIN_PAGE), entries().single().toRoute<Onboarding>())
        }
        composeRule.onNodeWithText(
            "Tu cuenta está suspendida por ahora, así que cerramos la sesión."
        )
            .assertExists()
    }

    @Test
    fun termsRequiredOpensA4WithoutSigningOut() {
        launch(Home, Parche())

        bus.publish(AppError.TermsRequired)
        composeRule.waitForIdle()

        assertEquals(emptyList<String>(), auth.calls)
        composeRule.runOnUiThread {
            assertEquals(Onboarding(page = LOGIN_PAGE), entries().single().toRoute<Onboarding>())
        }
    }

    @Test
    fun plusRequiredOpensG1OnTopWithTheScreenSource() {
        launch(Home, Parche())

        bus.publish(AppError.PlusRequired)
        composeRule.waitForIdle()
        // Con G1 ya abierta, otro 402 no apila una segunda.
        bus.publish(AppError.QuotaExceeded(QuotaKind.SIMULATION, null))
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            val stack = entries()
            assertEquals(3, stack.size)
            assertEquals(Paywall(PAYWALL_SOURCE_PARCHE), stack.last().toRoute<Paywall>())
            navController.popBackStack()
            assertEquals(true, navController.currentDestination?.route?.contains("Parche"))
        }
        assertEquals(emptyList<String>(), auth.calls)
    }

    @Test
    fun quotaExceededOpensTheG2OfThatQuota() {
        val resetsAt = Instant.fromEpochSeconds(1_800_000_000)
        launch(Home, Simulation("sim-1"))

        bus.publish(AppError.QuotaExceeded(QuotaKind.SIMULATION, resetsAt))
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            assertEquals(
                Paywall(PlusSources.limit(QuotaKind.SIMULATION, resetsAt)),
                entries().last().toRoute<Paywall>()
            )
        }
    }

    @Test
    fun splashAndA4HandleTheSessionThemselves() {
        launch(Splash)

        bus.publish(AppError.Unauthorized)
        bus.publish(AppError.TermsRequired)
        composeRule.waitForIdle()

        assertEquals(emptyList<String>(), auth.calls)
        composeRule.runOnUiThread { assertEquals(1, entries().size) }
    }
}
