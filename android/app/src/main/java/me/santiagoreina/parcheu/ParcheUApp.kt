package me.santiagoreina.parcheu

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.navigation.AppNavHost
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.NavControllerNavigator
import me.santiagoreina.parcheu.navigation.ParcheUNavBar
import me.santiagoreina.parcheu.navigation.Splash
import me.santiagoreina.parcheu.navigation.isTopLevel

/** Raíz de la UI: tema, NavBar en las raíces y el grafo de navegación completo. */
@Composable
fun ParcheUApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: AppRoute = Splash
) {
    val navigator = remember(navController) { NavControllerNavigator(navController) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    ParcheUTheme {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            bottomBar = {
                if (destination.isTopLevel()) {
                    ParcheUNavBar(
                        currentDestination = destination,
                        onSelect = { navigator.navigateToTopLevel(it.route) }
                    )
                }
            }
        ) { padding ->
            AppNavHost(
                navController = navController,
                navigator = navigator,
                startDestination = startDestination,
                modifier = Modifier.padding(padding)
            )
        }
    }
}
