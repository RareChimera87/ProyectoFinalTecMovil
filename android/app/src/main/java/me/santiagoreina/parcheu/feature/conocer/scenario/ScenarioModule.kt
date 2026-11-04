package me.santiagoreina.parcheu.feature.conocer.scenario

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

/** Dependencias de F3 (E4.8). El chat en sí usa `chatModule`. */
val conocerScenarioModule =
    module {
        single<ScenarioApi> { get<Retrofit>().create(ScenarioApi::class.java) }
        single<ScenarioRepository> { ScenarioRepositoryImpl(get(), get()) }
        viewModel { params -> ScenarioViewModel(params.get(), get()) }
    }
