package me.santiagoreina.parcheu.feature.introproposal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.santiagoreina.parcheu.feature.presentar.IntroProposalEntry
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.IntroProposal

/** Punto de entrada de la ruta [IntroProposal] (E3, E6.2): la pantalla vive en `feature/presentar`. */
@Composable
fun IntroProposalRoute(
    route: IntroProposal,
    navigator: AppNavigator,
    modifier: Modifier = Modifier
) {
    IntroProposalEntry(id = route.id, navigator = navigator, modifier = modifier)
}
