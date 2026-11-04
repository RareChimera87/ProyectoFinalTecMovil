package me.santiagoreina.parcheu.feature.privacysettings

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

/**
 * Dependencias de H3 (E8.2). `ModesRepository` viene de `modesModule` y `AuthRepository` (con el cierre de
 * sesión) de `authModule`.
 */
val privacySettingsModule =
    module {
        single<PrivacyApi> { get<Retrofit>().create(PrivacyApi::class.java) }
        single<PrivacySettingsRepository> { ApiPrivacySettingsRepository(get(), get()) }
        viewModel { PrivacySettingsViewModel(get(), get(), get()) }
    }
