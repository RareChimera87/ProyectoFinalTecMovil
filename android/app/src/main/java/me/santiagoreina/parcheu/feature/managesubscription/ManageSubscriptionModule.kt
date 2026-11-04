package me.santiagoreina.parcheu.feature.managesubscription

import me.santiagoreina.parcheu.feature.plus.ManageSubscriptionViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependencias de G4 (E7.3). */
val manageSubscriptionModule = module {
    viewModel { ManageSubscriptionViewModel(billing = get()) }
}
