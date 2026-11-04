package me.santiagoreina.parcheu.feature.traintwin

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import retrofit2.Retrofit

/** Dependencias de C4 (E9.3). Ya está registrado en AppModules.kt. */
val trainTwinModule =
    module {
        single<TrainTwinApi> { get<Retrofit>().create(TrainTwinApi::class.java) }
        single<TrainTwinRepository> { RemoteTrainTwinRepository(get(), get()) }
        viewModelOf(::TrainTwinViewModel)
    }
