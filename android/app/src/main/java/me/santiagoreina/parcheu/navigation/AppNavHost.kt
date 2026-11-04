package me.santiagoreina.parcheu.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import me.santiagoreina.parcheu.feature.addfriends.AddFriendsRoute
import me.santiagoreina.parcheu.feature.basicprofile.BasicProfileRoute
import me.santiagoreina.parcheu.feature.blockedusers.BlockedUsersRoute
import me.santiagoreina.parcheu.feature.chat.ChatRoute
import me.santiagoreina.parcheu.feature.chatlist.ChatListRoute
import me.santiagoreina.parcheu.feature.communities.CommunitiesRoute
import me.santiagoreina.parcheu.feature.consent.ConsentRoute
import me.santiagoreina.parcheu.feature.deleteaccount.DeleteAccountRoute
import me.santiagoreina.parcheu.feature.evolution.EvolutionRoute
import me.santiagoreina.parcheu.feature.exportdata.ExportDataRoute
import me.santiagoreina.parcheu.feature.friendsoffriends.FriendsOfFriendsRoute
import me.santiagoreina.parcheu.feature.home.HomeRoute
import me.santiagoreina.parcheu.feature.introduce.IntroduceRoute
import me.santiagoreina.parcheu.feature.introproposal.IntroProposalRoute
import me.santiagoreina.parcheu.feature.introresult.IntroResultRoute
import me.santiagoreina.parcheu.feature.managesubscription.ManageSubscriptionRoute
import me.santiagoreina.parcheu.feature.matchfound.MatchFoundRoute
import me.santiagoreina.parcheu.feature.matchqueue.MatchQueueRoute
import me.santiagoreina.parcheu.feature.modes.ModesRoute
import me.santiagoreina.parcheu.feature.notifications.NotificationsRoute
import me.santiagoreina.parcheu.feature.onboarding.OnboardingRoute
import me.santiagoreina.parcheu.feature.parche.ParcheRoute
import me.santiagoreina.parcheu.feature.paywall.PaywallRoute
import me.santiagoreina.parcheu.feature.privacysettings.PrivacySettingsRoute
import me.santiagoreina.parcheu.feature.profile.ProfileRoute
import me.santiagoreina.parcheu.feature.purchasesuccess.PurchaseSuccessRoute
import me.santiagoreina.parcheu.feature.report.ReportRoute
import me.santiagoreina.parcheu.feature.results.ResultsRoute
import me.santiagoreina.parcheu.feature.scenariochat.ScenarioChatRoute
import me.santiagoreina.parcheu.feature.simulation.SimulationRoute
import me.santiagoreina.parcheu.feature.simulations.SimulationsRoute
import me.santiagoreina.parcheu.feature.splash.SplashRoute
import me.santiagoreina.parcheu.feature.suspended.SuspendedRoute
import me.santiagoreina.parcheu.feature.traintwin.TrainTwinRoute
import me.santiagoreina.parcheu.feature.twinquestions.TwinQuestionsRoute
import me.santiagoreina.parcheu.feature.twinready.TwinReadyRoute
import me.santiagoreina.parcheu.feature.underage.UnderageRoute
import org.koin.compose.viewmodel.koinViewModel

/**
 * Grafo de navegación completo (ADR-0006). Declara todas las rutas de [AppRoute] con su `XRoute`; queda
 * congelado desde E0.7: cada feature cambia solo su `feature/<x>/XRoute.kt`.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    startDestination: AppRoute = Splash
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<Splash> { SplashRoute(it.toRoute(), navigator) }
        composable<Onboarding> { OnboardingRoute(it.toRoute(), navigator) }
        composable<BasicProfile> { BasicProfileRoute(it.toRoute(), navigator) }
        composable<Underage> { UnderageRoute(it.toRoute(), navigator) }
        composable<Suspended> { SuspendedRoute(it.toRoute(), navigator) }
        composable<TwinQuestions> { TwinQuestionsRoute(it.toRoute(), navigator) }
        composable<Modes> { ModesRoute(it.toRoute(), navigator) }
        composable<Consent> { ConsentRoute(it.toRoute(), navigator) }
        composable<TwinReady> { TwinReadyRoute(it.toRoute(), navigator) }
        composable<Home> { HomeRoute(it.toRoute(), navigator) }
        composable<Notifications> {
            NotificationsRoute(it.toRoute(), navigator, requestsViewModel = koinViewModel())
        }
        composable<Profile> { ProfileRoute(it.toRoute(), navigator) }
        composable<TrainTwin> { TrainTwinRoute(it.toRoute(), navigator) }
        composable<AddFriends> { AddFriendsRoute(it.toRoute(), navigator) }
        composable<Parche> { ParcheRoute(it.toRoute(), navigator) }
        composable<FriendsOfFriends> { FriendsOfFriendsRoute(it.toRoute(), navigator) }
        composable<Communities> { CommunitiesRoute(it.toRoute(), navigator) }
        composable<Evolution> { EvolutionRoute(it.toRoute(), navigator) }
        composable<Introduce> { IntroduceRoute(it.toRoute(), navigator) }
        composable<IntroResult> { IntroResultRoute(it.toRoute(), navigator) }
        composable<IntroProposal> { IntroProposalRoute(it.toRoute(), navigator) }
        composable<MatchQueue> { MatchQueueRoute(it.toRoute(), navigator) }
        composable<MatchFound> { MatchFoundRoute(it.toRoute(), navigator) }
        composable<ScenarioChat> { ScenarioChatRoute(it.toRoute(), navigator) }
        composable<Simulation> { SimulationRoute(it.toRoute(), navigator) }
        composable<Simulations> { SimulationsRoute(it.toRoute(), navigator) }
        composable<Results> { ResultsRoute(it.toRoute(), navigator) }
        composable<ChatList> { ChatListRoute(it.toRoute(), navigator) }
        composable<Chat> { ChatRoute(it.toRoute(), navigator) }
        composable<Paywall> { PaywallRoute(it.toRoute(), navigator) }
        composable<PurchaseSuccess> { PurchaseSuccessRoute(it.toRoute(), navigator) }
        composable<ManageSubscription> { ManageSubscriptionRoute(it.toRoute(), navigator) }
        composable<Report> { ReportRoute(it.toRoute(), navigator) }
        composable<PrivacySettings> { PrivacySettingsRoute(it.toRoute(), navigator) }
        composable<DeleteAccount> { DeleteAccountRoute(it.toRoute(), navigator) }
        composable<ExportData> { ExportDataRoute(it.toRoute(), navigator) }
        composable<BlockedUsers> { BlockedUsersRoute(it.toRoute(), navigator) }
    }
}
