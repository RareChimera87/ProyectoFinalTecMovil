package me.santiagoreina.parcheu.di

import me.santiagoreina.parcheu.core.network.networkModule
import me.santiagoreina.parcheu.data.auth.authModule
import me.santiagoreina.parcheu.data.billing.billingModule
import me.santiagoreina.parcheu.feature.addfriends.addFriendsModule
import me.santiagoreina.parcheu.feature.basicprofile.basicProfileModule
import me.santiagoreina.parcheu.feature.blockedusers.blockedUsersModule
import me.santiagoreina.parcheu.feature.chat.chatModule
import me.santiagoreina.parcheu.feature.chatlist.chatListModule
import me.santiagoreina.parcheu.feature.communities.communitiesModule
import me.santiagoreina.parcheu.feature.consent.consentModule
import me.santiagoreina.parcheu.feature.deleteaccount.deleteAccountModule
import me.santiagoreina.parcheu.feature.evolution.evolutionModule
import me.santiagoreina.parcheu.feature.exportdata.exportDataModule
import me.santiagoreina.parcheu.feature.friendsoffriends.friendsOfFriendsModule
import me.santiagoreina.parcheu.feature.home.homeModule
import me.santiagoreina.parcheu.feature.introduce.introduceModule
import me.santiagoreina.parcheu.feature.introproposal.introProposalModule
import me.santiagoreina.parcheu.feature.introresult.introResultModule
import me.santiagoreina.parcheu.feature.managesubscription.manageSubscriptionModule
import me.santiagoreina.parcheu.feature.matchfound.matchFoundModule
import me.santiagoreina.parcheu.feature.matchqueue.matchQueueModule
import me.santiagoreina.parcheu.feature.modes.modesModule
import me.santiagoreina.parcheu.feature.notifications.notificationsModule
import me.santiagoreina.parcheu.feature.notifications.requests.connectRequestsModule
import me.santiagoreina.parcheu.feature.onboarding.onboardingModule
import me.santiagoreina.parcheu.feature.parche.parcheModule
import me.santiagoreina.parcheu.feature.paywall.paywallModule
import me.santiagoreina.parcheu.feature.privacysettings.privacySettingsModule
import me.santiagoreina.parcheu.feature.profile.profileModule
import me.santiagoreina.parcheu.feature.purchasesuccess.purchaseSuccessModule
import me.santiagoreina.parcheu.feature.report.reportModule
import me.santiagoreina.parcheu.feature.results.resultsModule
import me.santiagoreina.parcheu.feature.scenariochat.scenarioChatModule
import me.santiagoreina.parcheu.feature.simulation.simulationModule
import me.santiagoreina.parcheu.feature.simulations.simulationsModule
import me.santiagoreina.parcheu.feature.splash.splashModule
import me.santiagoreina.parcheu.feature.traintwin.trainTwinModule
import me.santiagoreina.parcheu.feature.twinquestions.twinQuestionsModule
import me.santiagoreina.parcheu.feature.twinready.twinReadyModule
import me.santiagoreina.parcheu.feature.underage.underageModule
import org.koin.core.module.Module

/** Módulos de infraestructura compartida (red, sesión, errores, billing). */
val coreModules: List<Module> = listOf(networkModule, authModule, billingModule)

/**
 * Un módulo Koin por feature (ADR-0006). Todos existen desde E0.7, vacíos; cada feature llena solo el
 * suyo y este archivo queda congelado.
 */
val featureModules: List<Module> =
    listOf(
        splashModule,
        onboardingModule,
        basicProfileModule,
        underageModule,
        twinQuestionsModule,
        modesModule,
        consentModule,
        twinReadyModule,
        homeModule,
        notificationsModule,
        connectRequestsModule,
        profileModule,
        trainTwinModule,
        addFriendsModule,
        parcheModule,
        friendsOfFriendsModule,
        communitiesModule,
        evolutionModule,
        introduceModule,
        introResultModule,
        introProposalModule,
        matchQueueModule,
        matchFoundModule,
        scenarioChatModule,
        simulationModule,
        simulationsModule,
        resultsModule,
        chatListModule,
        chatModule,
        paywallModule,
        purchaseSuccessModule,
        manageSubscriptionModule,
        reportModule,
        privacySettingsModule,
        deleteAccountModule,
        exportDataModule,
        blockedUsersModule
    )

/** Todo lo que se carga al iniciar la app. */
val appModules: List<Module> = coreModules + featureModules
