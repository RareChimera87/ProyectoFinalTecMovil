package me.santiagoreina.parcheu.feature.purchasesuccess

import me.santiagoreina.parcheu.feature.plus.PurchaseSuccessViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependencias de G3 (E7.3): el plan llega como parámetro de la ruta. */
val purchaseSuccessModule = module {
    viewModel { params -> PurchaseSuccessViewModel(plan = params.get(), billing = get()) }
}
