package me.santiagoreina.parcheu.feature.evolution

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import retrofit2.Retrofit

/** Dependencias de D9 (E6.5). */
val evolutionModule =
    module {
        single<EvolutionApi> { get<Retrofit>().create(EvolutionApi::class.java) }
        single<EvolutionRepository> { EvolutionRepositoryImpl(get(), get()) }
        viewModelOf(::EvolutionViewModel)
    }
