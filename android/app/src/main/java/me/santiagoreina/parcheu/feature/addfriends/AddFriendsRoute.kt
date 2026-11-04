package me.santiagoreina.parcheu.feature.addfriends

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.feature.invites.InvitesRoute
import me.santiagoreina.parcheu.navigation.AddFriends
import me.santiagoreina.parcheu.navigation.AppNavigator

/** Punto de entrada de la ruta [AddFriends] (C5). La pantalla vive en `feature/invites` (E6.1). */
@Composable
fun AddFriendsRoute(route: AddFriends, navigator: AppNavigator, modifier: Modifier = Modifier) {
    InvitesRoute(route = route, navigator = navigator, modifier = modifier)
}
