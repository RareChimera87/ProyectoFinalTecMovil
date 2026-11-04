package me.santiagoreina.parcheu.feature.simulations

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependencias de F6 (E4.5). El repositorio de simulaciones lo registra `simulationModule`. */
val simulationsModule = module {
    viewModel { SimulationsViewModel(get()) }
}
