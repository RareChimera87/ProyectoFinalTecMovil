package me.santiagoreina.parcheu.navigation

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlin.reflect.KClass
import me.santiagoreina.parcheu.designsystem.R as DsR
import me.santiagoreina.parcheu.designsystem.components.base.NavBarItem
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUNavBar as DsNavBar

/**
 * Raíces de la NavBar (N0): Inicio, Parche, Conocer, Chats y Perfil. Los iconos son los de PU NavBar
 * (docs/mockups-inventario.md: home, hub, shuffle, chat_bubble, person); [activeIcon] es la variante
 * rellena (FILL 1) que lleva la pestaña activa. Referencias directas a R para que el reductor de recursos
 * de R8 los conserve.
 */
enum class TopLevelDestination(
    val label: String,
    val route: AppRoute,
    val routeClass: KClass<out AppRoute>,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val activeIcon: Int
) {
    HOME("Inicio", Home, Home::class, DsR.drawable.ic_home, DsR.drawable.ic_home_filled),
    PARCHE("Parche", Parche(), Parche::class, DsR.drawable.ic_hub, DsR.drawable.ic_hub_filled),
    CONOCER(
        "Conocer",
        MatchQueue(),
        MatchQueue::class,
        DsR.drawable.ic_shuffle,
        DsR.drawable.ic_shuffle_filled
    ),
    CHATS(
        "Chats",
        ChatList,
        ChatList::class,
        DsR.drawable.ic_chat_bubble,
        DsR.drawable.ic_chat_bubble_filled
    ),
    PERFIL("Perfil", Profile, Profile::class, DsR.drawable.ic_person, DsR.drawable.ic_person_filled)
}

/** La NavBar solo se muestra en las raíces; nunca en pantallas con botón atrás (arquitectura.md §14, #10). */
fun NavDestination?.isTopLevel(): Boolean =
    this != null && TopLevelDestination.entries.any { hasRoute(it.routeClass) }

/** Etiqueta de prueba de la pestaña de [destination]. */
fun navBarTabTag(destination: TopLevelDestination) = "navbar:${destination.name}"

/**
 * N0: la PU NavBar de `:designsystem` (76dp, píldora prc en la pestaña activa con icono relleno, rol de
 * pestaña y estado seleccionado para TalkBack) con los destinos de [TopLevelDestination].
 */
@Composable
fun ParcheUNavBar(
    currentDestination: NavDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    ParcheUNavBar(
        active = TopLevelDestination.entries.firstOrNull {
            currentDestination?.hasRoute(it.routeClass) == true
        },
        onSelect = onSelect,
        modifier = modifier
    )
}

/** Variante sin NavDestination (pruebas y capturas): [active] es la pestaña resaltada, o ninguna si es nula. */
@Composable
internal fun ParcheUNavBar(
    active: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = TopLevelDestination.entries.map { destination ->
        NavBarItem(
            key = destination.name,
            label = destination.label,
            icon = painterResource(destination.icon),
            activeIcon = painterResource(destination.activeIcon),
            testTag = navBarTabTag(destination)
        )
    }
    DsNavBar(
        items = items,
        activeKey = active?.name.orEmpty(),
        onSelect = { key -> onSelect(TopLevelDestination.valueOf(key)) },
        modifier = modifier.testTag("navbar")
    )
}
