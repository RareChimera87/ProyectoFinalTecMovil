package me.santiagoreina.parcheu.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlin.reflect.KClass
import me.santiagoreina.parcheu.data.auth.LOGIN_PAGE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M1 de la revisión de navegación: `replaceAll` (cierre de sesión, 401 global, borrado de cuenta,
 * suspensión, fin del onboarding) quitaba la pila visible pero no las pilas que cambiar de pestaña había
 * guardado; tras entrar con otra cuenta, tocar Chats restauraba el chat de la cuenta anterior con su
 * ViewModel vivo. Las pantallas llevan un ViewModel testigo para ver cuál sobrevive.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountSwitchSavedStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** ViewModel testigo: anota qué pantalla lo creó y si se destruyó. */
    class Probe(val name: String) : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    private val probes = mutableListOf<Probe>()
    private lateinit var navController: NavHostController
    private lateinit var navigator: NavControllerNavigator

    @Before
    fun setUp() {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController, startDestination = Home) {
                composable<Home> { Text("C1") }
                composable<Onboarding> { Text("A4") }
                composable<Parche> { probe("Parche") }
                composable<FriendsOfFriends> { probe("D6") }
                composable<ChatList> { probe("ChatList") }
                composable<Chat> { probe("Chat/${it.toRoute<Chat>().chatId}") }
                composable<Profile> { Text("C3") }
                composable<PrivacySettings> { Text("H3") }
            }
        }
        composeRule.runOnUiThread { navigator = NavControllerNavigator(navController) }
        composeRule.waitForIdle()
    }

    @Composable
    private fun probe(name: String) {
        viewModel { Probe(name).also { probes += it } }
        Text(name)
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
        it.destination.route?.substringAfterLast('.')?.substringBefore('/')
            ?.substringBefore('?')
    }

    private fun probeOf(name: String, index: Int = 0) = probes.filter { it.name == name }[index]

    @Test
    fun cerrarSesionYEntrarConOtraCuentaNoRestauraElChatAnterior() {
        go { navigateToTopLevel(ChatList) }
        go { navigate(Chat("chat-cuenta-vieja")) }
        // H3 está en Perfil: la pila de Chats queda guardada al cambiar de pestaña.
        go { navigateToTopLevel(Profile) }
        go { navigate(PrivacySettings) }

        go { replaceAll(Onboarding(page = LOGIN_PAGE)) } // Cerrar sesión.
        assertTrue(
            "el ViewModel del chat de la cuenta anterior debe destruirse",
            probeOf("Chat/chat-cuenta-vieja").cleared
        )
        go { replaceAll(Home) } // Entra otra cuenta.
        go { navigateToTopLevel(ChatList) }

        assertCurrent(ChatList::class)
        assertEquals(listOf("Home", "ChatList"), stack())
        assertEquals(
            "solo un chat creado en toda la prueba",
            1,
            probes.count {
                it.name.startsWith("Chat/")
            }
        )
        assertEquals("la lista de chats es nueva", 2, probes.count { it.name == "ChatList" })
        assertTrue(probeOf("ChatList", 0).cleared)
    }

    @Test
    fun el401DesdeOtraPestanaTambienBorraLasPilasGuardadas() {
        go { navigateToTopLevel(ChatList) }
        go { navigate(Chat("c-vieja")) }
        go { navigateToTopLevel(Parche()) }
        go { replaceAll(Onboarding(page = LOGIN_PAGE)) } // GlobalErrorHandler, SessionExpired.
        go { replaceAll(Home) }
        go { navigateToTopLevel(ChatList) }
        assertCurrent(ChatList::class)
        assertTrue(probeOf("Chat/c-vieja").cleared)
    }

    @Test
    fun twinReadyAbreUnParcheNuevoAunqueHubieraUnaPilaGuardada() {
        // Cuenta anterior: Parche → D6, luego otra pestaña (la pila de Parche queda guardada).
        go { navigateToTopLevel(Parche()) }
        go { navigate(FriendsOfFriends) }
        go { navigateToTopLevel(Profile) }
        go { replaceAll(Onboarding(page = LOGIN_PAGE)) }

        // B5 'Ver mi parche' de la cuenta nueva, tal como antes de esta corrección (y como hace ahora).
        go { replaceAll(Home) }
        go { navigateToTopLevel(Parche()) }
        assertCurrent(Parche::class)
        assertEquals(listOf("Home", "Parche"), stack())
        assertTrue(probeOf("D6").cleared)
        assertTrue(probeOf("Parche", 0).cleared)

        go { replaceAll(Home) }
        go { openTopLevel(Parche()) }
        assertCurrent(Parche::class)
        assertEquals(listOf("Home", "Parche"), stack())
    }

    @Test
    fun finDelOnboardingSigueDejandoInicioComoRaiz() {
        go { replaceAll(Onboarding()) }
        go { replaceAll(Home) }
        assertEquals(listOf("Home"), stack())
        go { navigateToTopLevel(Profile) }
        go { navigateToTopLevel(Home) }
        assertEquals(listOf("Home"), stack())
    }

    @Test
    fun irAInicioDesdeUnFlujoTerminalDescartaLaPestana() {
        // "Ir a inicio" de F5 y G3: al volver a la pestaña se empieza en su raíz.
        go { navigateToTopLevel(ChatList) }
        go { navigate(Chat("c1")) }
        go { leaveTopLevel() }
        assertCurrent(Home::class)
        assertTrue(probeOf("Chat/c1").cleared)
        go { navigateToTopLevel(ChatList) }
        assertEquals(listOf("Home", "ChatList"), stack())
    }
}
