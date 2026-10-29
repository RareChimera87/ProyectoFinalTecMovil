package me.santiagoreina.parcheu.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import me.santiagoreina.parcheu.R
import me.santiagoreina.parcheu.core.error.GlobalError
import me.santiagoreina.parcheu.core.error.GlobalErrorBus
import me.santiagoreina.parcheu.data.auth.LOGIN_PAGE
import me.santiagoreina.parcheu.feature.conocer.match.PAYWALL_SOURCE_MATCH_LIMIT
import me.santiagoreina.parcheu.feature.evolution.PAYWALL_SOURCE_EVOLUTION
import me.santiagoreina.parcheu.feature.home.PAYWALL_SOURCE_HOME
import me.santiagoreina.parcheu.feature.notifications.PAYWALL_SOURCE_NOTIFICATIONS
import me.santiagoreina.parcheu.feature.parche.PAYWALL_SOURCE_PARCHE
import me.santiagoreina.parcheu.feature.parche.communities.PAYWALL_SOURCE_COMMUNITIES
import me.santiagoreina.parcheu.feature.parche.fof.PAYWALL_SOURCE_FOF
import me.santiagoreina.parcheu.feature.plus.PlusSources
import me.santiagoreina.parcheu.feature.presentar.PRESENTAR_PAYWALL_SOURCE
import me.santiagoreina.parcheu.feature.results.RESULTS_PAYWALL_SOURCE
import me.santiagoreina.parcheu.feature.simulation.SIMULATION_PAYWALL_SOURCE

/** `source` de G1 cuando el 402 llega por el bus desde una pantalla sin origen propio. */
const val PAYWALL_SOURCE_GLOBAL = "app"

/**
 * Consume el [GlobalErrorBus] dentro de `ParcheUApp` (arquitectura.md §2.3), con el NavController real:
 * - sesión vencida o cuenta borrada: arranca el cierre de sesión de siempre ([startSignOut]:
 *   `BackgroundSignOut`, E8.2) y deja A4 como única pantalla en el acto, con un aviso breve;
 * - cuenta suspendida o baneada: deja `Suspended` como única pantalla **sin cerrar sesión**, para que la
 *   persona pueda descargar sus datos, borrar la cuenta o salir; en esas pantallas no hace nada;
 * - términos nuevos: A4 sin cerrar sesión, que muestra la hoja de aceptación;
 * - `PLUS_REQUIRED`: G1 con el origen de la pantalla visible; `QUOTA_EXCEEDED(kind)`: la G2 de ese cupo.
 *
 * Splash y A4 tratan la sesión ellos mismos (401, suspensión y términos son su flujo normal), así que con
 * ellos visibles no se hace nada. Mientras se cierra la sesión (desde cualquier pantalla: H3, borrar
 * cuenta...) el bus no publica 401, y los demás eventos encuentran A4 visible y se descartan; un 402 con G1
 * o G2 ya abierta no apila otra.
 */
@Composable
fun GlobalErrorHandler(
    bus: GlobalErrorBus,
    navController: NavHostController,
    navigator: AppNavigator,
    snackbar: SnackbarHostState,
    startSignOut: () -> Unit
) {
    val resources = LocalContext.current.resources
    val currentStartSignOut by rememberUpdatedState(startSignOut)
    val scope = rememberCoroutineScope()
    LaunchedEffect(bus, navController, navigator) {
        bus.errors.collect { error ->
            val destination = navController.currentDestination ?: return@collect
            if (destination.handlesSessionItself()) return@collect
            fun notify(message: Int) {
                scope.launch { snackbar.showSnackbar(resources.getString(message)) }
            }
            when (error) {
                GlobalError.SessionExpired -> {
                    notify(R.string.global_error_session_expired)
                    // `startSignOut` marca la sesión como cerrada antes de volver (A4 no llama a
                    // `POST /v1/session`) y el resto del cierre sigue en el scope de la app: se navega ya,
                    // sin esperar la red y sin depender de que este efecto siga vivo.
                    currentStartSignOut()
                    navigator.replaceAll(Onboarding(page = LOGIN_PAGE))
                }
                GlobalError.AccountSuspended -> {
                    // La sesión se conserva: las rutas exentas de la suspensión (exportar, borrar la
                    // cuenta, H3) son justo lo que la persona puede seguir haciendo (Ley 1581).
                    if (destination.allowedWhileSuspended()) return@collect
                    notify(R.string.global_error_suspended)
                    navigator.replaceAll(Suspended)
                }
                GlobalError.TermsRequired -> {
                    notify(R.string.global_error_terms)
                    navigator.replaceAll(Onboarding(page = LOGIN_PAGE))
                }
                GlobalError.PlusRequired, is GlobalError.QuotaExceeded -> {
                    if (destination.hasRoute(Paywall::class)) return@collect
                    navigator.navigate(paywallFor(error, paywallSourceFor(destination)))
                }
            }
        }
    }
}

private fun NavDestination.handlesSessionItself(): Boolean =
    hasRoute(Splash::class) || hasRoute(Onboarding::class)

/** Pantallas que una cuenta suspendida sí puede usar: el bloqueo mismo y las de sus derechos. */
private fun NavDestination.allowedWhileSuspended(): Boolean = hasRoute(Suspended::class) ||
    hasRoute(ExportData::class) ||
    hasRoute(DeleteAccount::class) ||
    hasRoute(PrivacySettings::class)

/** Igual que `PlusErrorRouter`: G2 del cupo si el servidor dijo cuál, si no G1 con [source]. */
private fun paywallFor(error: GlobalError, source: String): Paywall =
    if (error is GlobalError.QuotaExceeded && error.kind != null) {
        Paywall(PlusSources.limit(error.kind, error.resetsAt))
    } else {
        Paywall(source)
    }

/** Origen de G1 según la pantalla visible: el mismo que esa pantalla usa en su propio botón de ParcheU+. */
internal fun paywallSourceFor(destination: NavDestination): String = when {
    destination.hasRoute(Home::class) -> PAYWALL_SOURCE_HOME
    destination.hasRoute(Parche::class) -> PAYWALL_SOURCE_PARCHE
    destination.hasRoute(FriendsOfFriends::class) -> PAYWALL_SOURCE_FOF
    destination.hasRoute(Communities::class) -> PAYWALL_SOURCE_COMMUNITIES
    destination.hasRoute(Evolution::class) -> PAYWALL_SOURCE_EVOLUTION
    destination.hasRoute(Notifications::class) -> PAYWALL_SOURCE_NOTIFICATIONS
    destination.hasRoute(Introduce::class) ||
        destination.hasRoute(IntroResult::class) ||
        destination.hasRoute(IntroProposal::class) -> PRESENTAR_PAYWALL_SOURCE
    destination.hasRoute(MatchQueue::class) -> PAYWALL_SOURCE_MATCH_LIMIT
    destination.hasRoute(Simulation::class) -> SIMULATION_PAYWALL_SOURCE
    destination.hasRoute(Results::class) -> RESULTS_PAYWALL_SOURCE
    else -> PAYWALL_SOURCE_GLOBAL
}
