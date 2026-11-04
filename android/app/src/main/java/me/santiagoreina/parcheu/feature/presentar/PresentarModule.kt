package me.santiagoreina.parcheu.feature.presentar

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

/**
 * Dependencias de E1-E3 (E6.2): API, repositorio y los tres ViewModels. Lo incluye
 * `introduceModule` (ya registrado en AppModules.kt); `introResultModule` e
 * `introProposalModule` quedan vacíos.
 */
val presentarModule = module {
    single<IntroductionsApi> { get<Retrofit>().create(IntroductionsApi::class.java) }
    single<IntroductionsRepository> { RemoteIntroductionsRepository(get(), get()) }
    viewModel { IntroduceViewModel(get()) }
    viewModel { params -> IntroResultViewModel(params.get(), get()) }
    viewModel { params -> IntroProposalViewModel(params.get(), get()) }
}
