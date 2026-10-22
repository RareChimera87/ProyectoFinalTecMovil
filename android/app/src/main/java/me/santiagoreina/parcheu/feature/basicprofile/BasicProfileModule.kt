package me.santiagoreina.parcheu.feature.basicprofile

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

/** Dependencias de B0 (E2.6). */
val basicProfileModule = module {
    single<ProfileApi> { get<Retrofit>().create(ProfileApi::class.java) }
    single<BasicProfileRepository> { BasicProfileRepositoryImpl(get(), get()) }
    viewModel { params -> BasicProfileViewModel(edit = params.get(), repository = get(), auth = get()) }
}
