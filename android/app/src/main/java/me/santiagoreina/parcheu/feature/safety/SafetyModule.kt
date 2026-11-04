package me.santiagoreina.parcheu.feature.safety

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

/** Dependencias de reportar y bloquear (E8.1): H1 en cualquier pantalla y H2 en la ruta `Report`. */
val safetyModule =
    module {
        single<SafetyApi> { get<Retrofit>().create(SafetyApi::class.java) }
        single<SafetyRepository> { SafetyRepositoryImpl(get(), get()) }
        viewModel { params -> SafetySheetViewModel(params.get(), get()) }
        viewModel { params -> ReportViewModel(params.get(), get()) }
    }
