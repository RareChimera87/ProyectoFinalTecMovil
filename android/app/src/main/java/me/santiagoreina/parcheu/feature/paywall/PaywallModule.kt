package me.santiagoreina.parcheu.feature.paywall

import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.basicprofile.BasicProfileRepository
import me.santiagoreina.parcheu.feature.plus.PaywallViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependencias de G1 (E7.3). El billing lo registra `billingModule` (E7.2). */
val paywallModule = module {
    viewModel {
        val profile = get<BasicProfileRepository>()
        PaywallViewModel(billing = get()) { (profile.me() as? ApiResult.Success)?.value?.id }
    }
}
