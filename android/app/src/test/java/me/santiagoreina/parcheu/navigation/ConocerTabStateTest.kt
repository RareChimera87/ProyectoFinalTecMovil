package me.santiagoreina.parcheu.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlin.reflect.KClass
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.conocer.match.FakeMatchRepository
import me.santiagoreina.parcheu.feature.conocer.match.MatchQueueTags
import me.santiagoreina.parcheu.feature.conocer.match.MatchQueueViewModel
import me.santiagoreina.parcheu.feature.conocer.match.QueuePhase
import me.santiagoreina.parcheu.feature.conocer.match.matchedTicket
import me.santiagoreina.parcheu.feature.matchqueue.MatchQueueRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A1 de la revisión de navegación: tras cerrar F1 con la X, tocar "Conocer" restauraba la misma F1 (la pila
 * de la pestaña se guardaba al ir a Inicio), con su ViewModel en `Searching` pero sin ticket ni listener:
 * "Buscando a alguien…" para siempre. Usa [MatchQueueRoute] real sobre un NavHost mínimo y un repositorio
 * falso; cada F1 crea su ViewModel con el alcance de su entrada, como hace Koin en la app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConocerTabStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val repository = FakeMatchRepository()
    private val created = mutableListOf<MatchQueueViewModel>()
    private lateinit var navController: NavHostController
    private lateinit var navigator: NavControllerNavigator

    @Before
    fun setUp() {
        composeRule.setContent {
            navController = rememberNavController()
            ParcheUTheme {
                NavHost(navController, startDestination = Home) {
                    composable<Home> { Text("C1") }
                    composable<MatchQueue> { entry ->
                        val route = entry.toRoute<MatchQueue>()
                        val vm = viewModel {
                            MatchQueueViewModel(route.mode, repository).also { created += it }
                        }
                        MatchQueueRoute(route, navigator, viewModel = vm)
                    }
                    composable<MatchFound> { Text("F2") }
                    composable<ChatList> { Text("Chats") }
                    composable<Profile> { Text("C3") }
                }
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

    @Test
    fun laXYLuegoConocerAbreUnaF1NuevaQueBusca() {
        go { navigateToTopLevel(MatchQueue()) }
        assertEquals(1, created.size)
        assertEquals(listOf(Mode.AMISTAD), repository.enqueued)

        composeRule.onNodeWithTag(MatchQueueTags.CANCEL).performClick()
        composeRule.waitForIdle()
        assertEquals(1, repository.cancelCalls)
        assertCurrent(Home::class)

        go { navigateToTopLevel(MatchQueue()) }
        assertCurrent(MatchQueue::class)
        assertEquals("F1 debe tener un ViewModel nuevo", 2, created.size)
        assertNotSame(created[0], created[1])
        assertEquals(
            "la F1 nueva entra a la fila",
            listOf(Mode.AMISTAD, Mode.AMISTAD),
            repository.enqueued
        )
        assertEquals(QueuePhase.Searching, created[1].state.value.phase)
        composeRule.onNodeWithTag(MatchQueueTags.SEARCHING).assertExists()
    }

    @Test
    fun cambiarDePestanaSinCancelarConservaLaBusqueda() {
        go { navigateToTopLevel(MatchQueue()) }
        go { navigateToTopLevel(ChatList) }
        go { navigateToTopLevel(Profile) }
        // El listener sigue vivo con F1 oculta: el match llega mientras la persona está en otra pestaña.
        composeRule.runOnUiThread { repository.tickets.tryEmit(matchedTicket("m9")) }
        composeRule.waitForIdle()
        assertEquals(QueuePhase.Matched("m9"), created.single().state.value.phase)

        go { navigateToTopLevel(MatchQueue()) }
        assertEquals("misma F1, sin volver a encolar", 1, created.size)
        assertEquals(1, repository.enqueued.size)
        assertEquals(0, repository.cancelCalls)
        // Al volver, F1 entrega el match que encontró en segundo plano y abre F2.
        assertCurrent(MatchFound::class)
        assertEquals(
            MatchFound("m9"),
            navController.currentBackStackEntry?.toRoute<MatchFound>()
        )
    }

    @Test
    fun cambiarDePestanaBuscandoYVolverSigueBuscando() {
        go { navigateToTopLevel(MatchQueue()) }
        go { navigateToTopLevel(ChatList) }
        go { navigateToTopLevel(MatchQueue()) }
        assertCurrent(MatchQueue::class)
        assertEquals(1, created.size)
        assertEquals(1, repository.enqueued.size)
        assertEquals(QueuePhase.Searching, created.single().state.value.phase)
    }
}
