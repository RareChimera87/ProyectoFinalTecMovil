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

    /**
     * Cambia de pestaña de la NavBar conservando el estado de cada raíz. Si [route] es la pestaña activa,
     * vuelve a su raíz (quita lo que tenga encima).
     */
    fun navigateToTopLevel(route: AppRoute)

    /**
     * Abre la raíz [route] desde el contenido de una pantalla (botón "Match al azar" de C1, un deep link),
     * como cambio de pestaña pero sin restaurar la pila guardada de esa pestaña: el usuario pidió empezar
     * ahí, no volver a donde la dejó. Por defecto es [navigateToTopLevel].
     */
    fun openTopLevel(route: AppRoute) = navigateToTopLevel(route)

    /** Deja [route] como única pantalla de la pila (fin del onboarding, cierre de sesión). */
    fun replaceAll(route: AppRoute)

    /** Vuelve atrás; `false` si no había adónde volver. */
    fun back(): Boolean

    /** `true` si hay una pantalla debajo y la actual no es una raíz de la NavBar. */
    fun canGoBack(): Boolean
}

class NavControllerNavigator(private val navController: NavHostController) : AppNavigator {
    /** Una raíz de la NavBar nunca se apila como hija: se abre como pestaña (deep links, efectos, botones). */
    override fun navigate(route: AppRoute) {
        if (route.isTopLevelRoute()) openTopLevel(route) else navController.navigate(route)
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

    override fun navigateToTopLevel(route: AppRoute) = toTopLevel(route, restore = true)

    override fun openTopLevel(route: AppRoute) = toTopLevel(route, restore = false)

    /**
     * Cada pestaña vive encima de Home: [Home, raíz de la pestaña, hijas...]. Las raíces se abren siempre
     * con esta función (nunca con navigate), de modo que la pila guardada de una pestaña solo contiene sus
     * propias hijas; antes C1 abría F1 con navigate, F1 quedaba guardada en la pila de Inicio y tocar
     * Inicio la restauraba (ADR-0006).
     */
    private fun toTopLevel(route: AppRoute, restore: Boolean) {
        val target = route::class
        // Las raíces se abren siempre encima de Home, así que en la pila hay a lo sumo una raíz distinta de
        // Home: esa es la pestaña activa (o Home si no hay ninguna). currentBackStack es API restringida.
        val currentTab = TopLevelDestination.entries
            .filter { it.routeClass != Home::class }
            .firstOrNull {
                runCatching { navController.getBackStackEntry(it.routeClass) }.isSuccess
            }
            ?.routeClass ?: Home::class
        if (currentTab == target) {
            if (restore) {
                // Volver a tocar la pestaña activa: queda solo su raíz.
                navController.popBackStack(target, inclusive = false)
            } else {
                // Desde el contenido, la raíz se reabre con los argumentos nuevos (p. ej. el modo de F1).
                navController.navigate(route) {
                    popUpTo(target) { inclusive = true }
                    launchSingleTop = true
                }
            }
            return
        }
        // Inicio no se "navega": basta con quitar lo que hay encima de Home. Con navigate + restoreState,
        // Navigation restauraría la pila guardada al hacer popUpTo<Home>, que queda asociada también al id
        // de Home (la de otra pestaña), y tocar Inicio mostraba esa otra pestaña.
        if (target == Home::class &&
            navController.popBackStack<Home>(inclusive = false, saveState = restore)
        ) {
            return
        }
        navController.navigate(route) {
            // Home es la raíz real tras replaceAll; Splash ya no está en la pila y Navigation ignoraría el popUpTo.
            popUpTo<Home> { saveState = restore }
            launchSingleTop = true
            restoreState = restore
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

/** `true` si [this] es la ruta de una raíz de la NavBar. */
fun AppRoute.isTopLevelRoute(): Boolean =
    TopLevelDestination.entries.any { it.routeClass == this::class }
