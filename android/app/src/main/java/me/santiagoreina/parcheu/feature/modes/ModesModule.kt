package me.santiagoreina.parcheu.feature.modes

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

/** Dependencias de B2/B3 (E2.5). `ModesRepository` también lo usa B5 (twinReadyModule). */
val modesModule =
    module {
        single<ModesApi> { get<Retrofit>().create(ModesApi::class.java) }
        single<ModesRepository> { ApiModesRepository(get(), get()) }
        viewModel { ModesViewModel(get()) }
    }
