package me.santiagoreina.parcheu.feature.simulation

import me.santiagoreina.parcheu.data.simulation.FirestoreSimulationLiveSource
import me.santiagoreina.parcheu.data.simulation.RemoteSimulationRepository
import me.santiagoreina.parcheu.data.simulation.SimulationApi
import me.santiagoreina.parcheu.data.simulation.SimulationLiveSource
import me.santiagoreina.parcheu.data.simulation.SimulationRepository
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

/**
 * Dependencias de F4/F5 (E4.5). También registra el repositorio de simulaciones que comparte la lista
 * de [me.santiagoreina.parcheu.feature.simulations.simulationsModule].
 */
val simulationModule = module {
    single<SimulationApi> { get<Retrofit>().create(SimulationApi::class.java) }
    single<SimulationRepository> { RemoteSimulationRepository(get(), get()) }
    single<SimulationLiveSource> { FirestoreSimulationLiveSource() }
    viewModel { params -> SimulationViewModel(params.get(), get(), get()) }
}
