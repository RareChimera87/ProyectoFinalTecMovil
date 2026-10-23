package me.santiagoreina.parcheu.feature.home

import me.santiagoreina.parcheu.data.auth.FirebaseAuthSource
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

/**
 * Dependencias de C1 / I1 (E4.7): API de Inicio y el ViewModel. El repositorio de simulaciones (para
 * "Simular" y el avance de la tarjeta en curso) lo registra `simulationModule`.
 */
val homeModule = module {
    single<HomeApi> { get<Retrofit>().create(HomeApi::class.java) }
    single<HomeRepository> { RemoteHomeRepository(get(), get()) }
    viewModel {
        val auth = get<FirebaseAuthSource>()
        HomeViewModel(get(), get()) { auth.authOrNull()?.currentUser?.displayName }
    }
}
