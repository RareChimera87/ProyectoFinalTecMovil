package me.santiagoreina.parcheu.navigation

import androidx.navigation.NavHostController
import kotlin.reflect.KClass

/**
 * Lo que una pantalla puede pedirle a la navegación. Cada `XRoute` recibe uno; así las features no
 * dependen de NavController y sus tests usan un fake.
 */
interface AppNavigator {
    /** Abre [route] encima de la pantalla actual. */
    fun navigate(route: AppRoute)

    /**
     * Abre [route] quitando antes [replacing] de la pila (con todo lo que tenga encima), si estaba.
     * Evita apilar una segunda copia de una pantalla que ya existe. Por defecto solo navega, para
     * no romper implementaciones (fakes de tests) que no lo necesitan.
     */
    fun navigateReplacing(route: AppRoute, replacing: KClass<out AppRoute>) = navigate(route)

    /**
     * Quita de la pila [replacing] y todo lo que haya encima (si no está, solo la pantalla actual) y abre
     * [route] como entrada nueva, sin reutilizar ninguna (con su propio ViewModel). Para destinos cuyo
     * argumento debe aplicarse siempre, p. ej. D8 -> D1 filtrado. Por defecto solo navega.
     */
    fun navigateAsNew(route: AppRoute, replacing: KClass<out AppRoute>) = navigate(route)

    /** Cambia de pestaña de la NavBar conservando el estado de cada raíz. */
    fun navigateToTopLevel(route: AppRoute)

    /** Deja [route] como única pantalla de la pila (fin del onboarding, cierre de sesión). */
    fun replaceAll(route: AppRoute)

    /** Vuelve atrás; `false` si no había adónde volver. */
    fun back(): Boolean

    /** `true` si hay una pantalla debajo y la actual no es una raíz de la NavBar. */
    fun canGoBack(): Boolean
}

class NavControllerNavigator(private val navController: NavHostController) : AppNavigator {
    override fun navigate(route: AppRoute) {
        navController.navigate(route)
    }

    override fun navigateReplacing(route: AppRoute, replacing: KClass<out AppRoute>) {
        navController.navigate(route) {
            popUpTo(replacing) { inclusive = true }
            launchSingleTop = true
        }
    }

    override fun navigateAsNew(route: AppRoute, replacing: KClass<out AppRoute>) {
        // Sin launchSingleTop: una entrada reutilizada conservaría su ViewModel y el argumento nuevo se ignoraría.
        if (!navController.popBackStack(replacing, inclusive = true)) navController.popBackStack()
        navController.navigate(route)
    }

    override fun navigateToTopLevel(route: AppRoute) {
        navController.navigate(route) {
            // Home es la raíz real tras replaceAll; Splash ya no está en la pila y Navigation ignoraría el popUpTo.
            popUpTo<Home> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    override fun replaceAll(route: AppRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    override fun back(): Boolean = navController.popBackStack()

    // Las raíces de la NavBar nunca muestran "Volver", aunque Home quede debajo de ellas.
    override fun canGoBack(): Boolean = navController.previousBackStackEntry != null &&
        !navController.currentDestination.isTopLevel()
}
