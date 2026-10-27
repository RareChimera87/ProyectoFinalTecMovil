package me.santiagoreina.parcheu.feature.profile

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

/**
 * Dependencias de C3 (E9.1). `ProfileApi` (cuenta y catálogo) lo registra `basicProfileModule`;
 * aquí
 * solo viven las rutas del gemelo y el ViewModel.
 */
val profileModule = module {
    single<TwinProfileApi> { get<Retrofit>().create(TwinProfileApi::class.java) }
    single<ProfileRepository> { RemoteProfileRepository(get(), get(), get()) }
    viewModel { ProfileViewModel(get()) }
}
