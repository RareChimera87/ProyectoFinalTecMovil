package me.santiagoreina.parcheu.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlin.reflect.KClass

/** Raíces de la NavBar (N0): Inicio, Parche, Conocer, Chats y Perfil. */
enum class TopLevelDestination(val label: String, val route: AppRoute, val routeClass: KClass<out AppRoute>) {
    HOME("Inicio", Home, Home::class),
    PARCHE("Parche", Parche(), Parche::class),
    CONOCER("Conocer", MatchQueue(), MatchQueue::class),
    CHATS("Chats", ChatList, ChatList::class),
    PERFIL("Perfil", Profile, Profile::class),
}

/** La NavBar solo se muestra en las raíces; nunca en pantallas con botón atrás (arquitectura.md §14, #10). */
fun NavDestination?.isTopLevel(): Boolean = this != null && TopLevelDestination.entries.any { hasRoute(it.routeClass) }

/**
 * Stub de N0 con Material 3. E1.4 lo reemplaza por el componente PU NavBar de `:designsystem`
 * (iconos Material Symbols Rounded, tokens y estado activo de la sección D/N0 de los mockups).
 */
@Composable
fun ParcheUNavBar(
    currentDestination: NavDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier.testTag("navbar")) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination?.hasRoute(destination.routeClass) == true
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    Text(
                        text = destination.label.take(1),
                        modifier = Modifier.size(24.dp).clip(CircleShape),
                    )
                },
                label = { Text(destination.label) },
                modifier = Modifier.testTag("navbar:${destination.name}"),
            )
        }
    }
}
