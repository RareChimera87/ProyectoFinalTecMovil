package me.santiagoreina.parcheu.feature.results

import me.santiagoreina.parcheu.data.results.RemoteResultsRepository
import me.santiagoreina.parcheu.data.results.ResultsApi
import me.santiagoreina.parcheu.data.results.ResultsRepository
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

/** Dependencias de F7/F8 (E4.6). Ya está registrado en AppModules.kt. */
val resultsModule = module {
    single<ResultsApi> { get<Retrofit>().create(ResultsApi::class.java) }
    single<ResultsRepository> { RemoteResultsRepository(get(), get()) }
    viewModel { params -> ResultsViewModel(params.get(), get()) }
}
