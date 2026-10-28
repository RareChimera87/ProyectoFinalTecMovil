package me.santiagoreina.parcheu.feature.parche.communities

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import retrofit2.Retrofit

/** Dependencias de D8 (E6.4). */
val communitiesFeatureModule =
    module {
        single<CommunitiesApi> { get<Retrofit>().create(CommunitiesApi::class.java) }
        single<CommunitiesRepository> { CommunitiesRepositoryImpl(get(), get()) }
        viewModelOf(::CommunitiesViewModel)
    }
