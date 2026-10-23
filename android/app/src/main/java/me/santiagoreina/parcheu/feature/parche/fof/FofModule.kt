package me.santiagoreina.parcheu.feature.parche.fof

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import retrofit2.Retrofit

/** Dependencias de D6/D7 (E6.3). Reutiliza el `GraphRepository` de Tu parche para el fondo de G0. */
val fofModule =
    module {
        single<FofApi> { get<Retrofit>().create(FofApi::class.java) }
        single<FofRepository> { FofRepositoryImpl(get(), get(), get()) }
        viewModelOf(::FofViewModel)
    }
