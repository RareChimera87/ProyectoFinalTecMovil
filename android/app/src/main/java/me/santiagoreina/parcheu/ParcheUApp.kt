package me.santiagoreina.parcheu

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.navigation.AppNavHost
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.Home
import me.santiagoreina.parcheu.navigation.NavControllerNavigator
import me.santiagoreina.parcheu.navigation.ParcheUNavBar
import me.santiagoreina.parcheu.navigation.Splash
import me.santiagoreina.parcheu.navigation.isTopLevel
import me.santiagoreina.parcheu.push.PendingDeepLink
import me.santiagoreina.parcheu.push.PushTokenRegistrar
import me.santiagoreina.parcheu.push.RequestNotificationPermissionOnce
import org.koin.core.context.GlobalContext

/** Raíz de la UI: tema, NavBar en las raíces y el grafo de navegación completo. */
@Composable
fun ParcheUApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: AppRoute = Splash,
) {
    val navigator = remember(navController) { NavControllerNavigator(navController) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val topLevel = destination.isTopLevel()

    // Deep link pendiente (toque en una notificación): se abre cuando la sesión está lista, es decir, cuando
    // Home está en la pila (ya pasaron Splash, login y onboarding). No importa qué pantalla esté visible:
    // un aviso de otro chat también se abre estando dentro de un chat.
    val pending by PendingDeepLink.route.collectAsState()
    val sessionReady = backStackEntry != null && navController.currentBackStack.value.any {
        it.destination.hasRoute(Home::class)
    }
    LaunchedEffect(pending, sessionReady) {
        val route = pending
        if (route != null && sessionReady) {
            PendingDeepLink.consume(route)
            navigator.navigate(route)
        }
    }

    // Permiso de notificaciones tras B5: se pide una vez al llegar a Inicio y entonces se registra el token FCM.
    val scope = rememberCoroutineScope()
    RequestNotificationPermissionOnce(
        active = destination?.hasRoute(Home::class) == true,
        context = LocalContext.current,
        onDone = {
            val registrar = GlobalContext.getOrNull()?.getOrNull<PushTokenRegistrar>()
            if (registrar != null) scope.launch { registrar.sync() }
        },
    )
    ParcheUTheme {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            bottomBar = {
                if (topLevel) {
                    ParcheUNavBar(
                        currentDestination = destination,
                        onSelect = { navigator.navigateToTopLevel(it.route) },
                    )
                }
            },
        ) { padding ->
            AppNavHost(
                navController = navController,
                navigator = navigator,
                startDestination = startDestination,
                modifier = Modifier.padding(padding),
            )
        }
    }
}
