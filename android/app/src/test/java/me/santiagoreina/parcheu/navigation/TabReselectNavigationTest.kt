package me.santiagoreina.parcheu.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlin.reflect.KClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fallo visto en producción (0.2.2): C1 → "Match al azar" (F1) → … → otra pestaña → tocar Inicio mostraba F1
 * con "Conocer" resaltada, porque F1 se había abierto con navigate y quedó guardada en la pila de Inicio.
 * Grafo mínimo con las rutas reales y pantallas vacías: lo que se prueba es [NavControllerNavigator].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TabReselectNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController
    private lateinit var navigator: NavControllerNavigator

    @Before
    fun setUp() {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController, startDestination = Home) {
                composable<Home> { Text("C1") }
                composable<Parche> { Text("D1") }
                composable<MatchQueue> { Text("F1") }
                composable<MatchFound> { Text("F2") }
                composable<Simulation> { Text("F4") }
                composable<Results> { Text("F7") }
                composable<ChatList> { Text("Chats") }
                composable<Chat> { Text("F9") }
                composable<Profile> { Text("C3") }
            }
        }
        composeRule.runOnUiThread { navigator = NavControllerNavigator(navController) }
        composeRule.waitForIdle()
    }

    private fun go(action: AppNavigator.() -> Unit) {
        composeRule.runOnUiThread { navigator.action() }
        composeRule.waitForIdle()
    }

    private fun assertCurrent(route: KClass<out AppRoute>) {
        val current = navController.currentDestination
        assertTrue(
            "actual = ${current?.route}, esperado = ${route.simpleName}",
            current?.hasRoute(route) == true
        )
    }

    private fun stack(): List<String?> = navController.currentBackStack.value.mapNotNull {
        it.destination.route?.substringAfterLast('.')
    }

    @Test
    fun tocarInicioTrasMatchAlAzarVuelveAInicio() {
        // Lo que hace C1 (onRandomMatch) y el resto del flujo F1 → F2 → F4 → F7 → F9.
        go { openTopLevel(MatchQueue()) }
        go { navigate(MatchFound("m1")) }
        go { navigate(Simulation("s1")) }
        go { navigate(Results("s1")) }
        go { navigate(Chat("c1")) }
        assertCurrent(Chat::class)

        go { navigateToTopLevel(ChatList) }
        assertCurrent(ChatList::class)
        go { navigateToTopLevel(Home) }
        assertCurrent(Home::class)
        assertEquals("solo Home en la pila", 1, stack().size)
    }

    @Test
    fun navigateAUnaRaizCuentaComoCambioDePestana() {
        // Un deep link o un efecto que use navigate(MatchQueue()) tampoco debe apilar F1 encima de Inicio.
        go { navigate(MatchQueue()) }
        go { navigateToTopLevel(Parche()) }
        go { navigateToTopLevel(Home) }
        assertCurrent(Home::class)
    }

    @Test
    fun tocarLaPestanaActivaVuelveASuRaiz() {
        go { navigateToTopLevel(ChatList) }
        go { navigate(Chat("c1")) }
        go { navigateToTopLevel(ChatList) }
        assertCurrent(ChatList::class)

        go { openTopLevel(MatchQueue()) }
        go { navigate(MatchFound("m1")) }
        go { navigateToTopLevel(MatchQueue()) }
        assertCurrent(MatchQueue::class)
    }

    @Test
    fun cambiarDePestanaConservaLaPilaDeCadaUna() {
        go { navigateToTopLevel(ChatList) }
        go { navigate(Chat("c1")) }
        go { navigateToTopLevel(Profile) }
        go { navigateToTopLevel(ChatList) }
        assertCurrent(Chat::class)
        // El atrás del sistema sigue funcionando dentro de la pestaña.
        go { back() }
        assertCurrent(ChatList::class)
    }

    @Test
    fun matchAlAzarEmpiezaDeCeroAunqueConocerTengaPilaGuardada() {
        go { openTopLevel(MatchQueue()) }
        go { navigate(MatchFound("m1")) }
        go { navigateToTopLevel(Home) }
        go { openTopLevel(MatchQueue()) }
        assertCurrent(MatchQueue::class)
    }

    /** Lo que hace MatchQueueRoute con MatchQueueEffect.Close (la X de F1). */
    private fun closeF1() = go { if (canGoBack()) back() else navigateToTopLevel(Home) }

    @Test
    fun laXDeF1LlevaAInicioAunqueConocerTengaHistoria() {
        go { openTopLevel(MatchQueue()) }
        go { navigate(MatchFound("m1")) }
        go { navigateToTopLevel(ChatList) }
        go { navigateToTopLevel(MatchQueue()) }
        go { back() }
        assertCurrent(MatchQueue::class)
        closeF1()
        assertCurrent(Home::class)
        go { navigateToTopLevel(Home) }
        assertCurrent(Home::class)
    }

    @Test
    fun elAtrasDelSistemaSaleDeF1() {
        go { openTopLevel(MatchQueue()) }
        go { back() }
        assertCurrent(Home::class)
    }
}
